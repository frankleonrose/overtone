(ns
  ^{:doc "Code that is common to many ugens."
     :author "Jeff Rose & Christophe McKeon"}
  overtone.sc.machinery.ugen.common
  (:use [overtone.helpers lib]
        [overtone.sc.machinery.ugen special-ops]))


(defn real-ugen-name
  [ugen]
  (overtone-ugen-name
    (case (:name ugen)
      "UnaryOpUGen"
      (get REVERSE-UNARY-OPS (:special ugen))

      "BinaryOpUGen"
      (get REVERSE-BINARY-OPS (:special ugen))

      (:name ugen))))

(defn special-ugen-name
  [ugen]
  (overtone-ugen-name
   (case (:name ugen)
     "UnaryOpUGen"
     (str (get REVERSE-UNARY-OPS (:special ugen)) "-unary")

     "BinaryOpUGen"
     (str (get REVERSE-BINARY-OPS (:special ugen)) "-binary")

     (:name ugen))))