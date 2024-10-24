(ns overtone.studio.pattern
  "Inspired by the SuperCollider pattern libary

  In our case patterns are just Clojure sequences, finite or infinite."
  (:require
   [overtone.algo.chance :as chance]))

(defn pfirst
  "Like first, but with \"pattern semantics\". Recursively descends into into
  sequences. Returns its argument when given something that isn't sequential."
  [s]
  (if (sequential? s)
    (let [v (first s)]
      (if (sequential? v)
        (recur v)
        v))
    s))

(defn- subtract-dur [durs durspec amount]
  (if-not (seq durs)
    (recur durspec durspec amount)
    (let [[d & ds] durs]
      (cond
        (< amount d)
        (cons (- d amount) ds)
        (<= d amount)
        (recur ds durspec (- amount d))))))

(defn- subtract-meta-dur [s dur]
  (when s
    (if-not (:durspec (meta s))
      (recur (vary-meta s (fn [m] (assoc m :durspec (:dur m)))) dur)
      (vary-meta
       s
       (fn [m]
         (update m :dur subtract-dur (:durspec m) dur))))))

(defn pnext
  "Like next, but with \"pattern semantics\". Lazily flattens nested sequences.

  When called with `dur` argument, will honor the `:dur` in metadata, possibly
  repeating elements if their duration hasn't finished.

      (pbind {:degree [:i :i :iii :ii :i :iv :v]
              :dur    [1/2 1 1/2 1/2 1/2 1/2 1/2]
              :root   ^{:dur 8} [:c3 :d3 :b2 :c3]})
  "
  ([s]
   (when (sequential? s)
     (let [[x & xs] s]
       (cond
         (sequential? x)
         (recur
          (with-meta
            (if (seq x)
              (concat x xs)
              xs)
            (meta s)))

         (some? xs)
         (with-meta xs (meta s))

         :else nil))))
  ([s dur]
   (if-let [sdurs (:dur (meta s))]
     (let [s     (if (sequential? sdurs)
                   s
                   (vary-meta s update :dur vector))
           sdurs (:dur (meta s))
           fdur  (pfirst sdurs)
           ndurs (pnext sdurs)]
       (if (<= fdur dur)
         ;; drop first, recur with next and adjusted dur
         (recur (-> s
                    pnext
                    (subtract-meta-dur dur))
                (- dur fdur))
         ;; keep first, adjust dur
         (subtract-meta-dur s dur)))
     (pnext s))))

(defn- pbind*
  "Internal helper for pbind, complicated by the fact that we want to cycle all
  seqs until we've fully consumed the longest seq, but we can't count because
  seqs could be infinite. So we track for which seqs we've reached the end at
  least once (done set)."
  [ks specs seqs done]
  (when-not (= (count done) (count ks))
    (let [vs (map pfirst seqs)
          vm (zipmap ks vs)]
      (cons
       vm
       (lazy-seq
        (pbind*
         ks
         specs
         (map (fn [v spec]
                (if (sequential? v)
                  (let [n (pnext v (:dur vm 1))]
                    (if (nil? n)
                      spec
                      n))
                  v))
              seqs
              specs)
         (into done
               (remove nil?
                       (map (fn [k s]
                              (when (and (sequential? s)
                                         (not (pnext s (:dur vm 1))))
                                k))
                            ks
                            seqs)))))))))

(defn pbind
  "Takes a map, with some of the map values seqs. Returns a sequence of maps, with
  each successive map value constructed by taking the next value of each
  sequence. Sequences wrap (cycle) until the longest sequence has been consumed.
  Non-sequential values are retained as-is.

  Similar to SuperCollider's `PBind`, part of the Pattern library. "
  ([m repeat]
   (cond
     (= 0 repeat)
     nil
     (= Float/POSITIVE_INFINITY repeat)
     (concat (pbind m) (lazy-seq (pbind m repeat)))
     :else
     (concat (pbind m) (lazy-seq (pbind m (dec repeat))))))
  ([m]
   (let [ks (keys m)
         specs (map m ks)
         seqs (map (fn [v]
                     (if (sequential? v)
                       (when-let [s (seq v)]
                         (with-meta s (meta v)))
                       v))
                   specs)
         done (set (remove nil?
                           (map (fn [k s]
                                  (when (not (sequential? s))
                                    k))
                                ks
                                seqs)))]
     (if  (= (count done) (count ks))
       [m]
       (pbind*
        ks
        specs
        seqs
        done)))))

(defn pwhite
  "Repeatedly sample random numbers from a uniform distribution.

  Takes the `min` and `max` of the sampled range, and an optional number of `repeats` (defaults to infinite).

  Using integers for `min` and `max` will yield integers, using float/double
  values will yield doubles."
  ([min max]
   (repeatedly #(chance/rrand min max)))
  ([min max repeats]
   (repeatedly repeats #(chance/rrand min max))))

(defmacro pdo
  "Macro, wraps a block which gets repeatedly called to yield sequence values."
  [& body]
  `(repeatedly (fn [] ~@body)))

(defn pseries
  "Generate a number series with a given start, step, and size."
  ([start step]
   (iterate #(+ step %) start))
  ([start step size]
   (if (= Float/POSITIVE_INFINITY size)
     (pseries start step)
     (take size (pseries start step)))))

(defn pchoose
  "Randomly choose value from a collection with `rand-nth`."
  [coll]
  (pdo (rand-nth coll)))

(defn ppad
  "Pad the `pattern` with a rest so the total duration of the pattern is a
  multiple of `beats`."
  [pattern beats]
  (let [length (apply + (map #(get % :dur 1) pattern))
        remain (- beats (mod length beats))]
    (if (< 0 remain)
      (concat pattern [{:type :rest
                        :dur remain}])
      pattern)))
