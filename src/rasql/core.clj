(ns rasql.core
  (:require [clojure.string :as str]))

(defn wrap-parens [s]
  (str "(" s ")"))

(defprotocol IRelation
  (base [this])
  (alt [this])
  (sel [this])
  (proj [this])
  (joins [this]))

(deftype Relation [base-relation relation-alias selection projection joins]
  IRelation
  clojure.lang.ILookup
  (base [_] base-relation)
  (alt [_] relation-alias)
  (sel [_] selection)
  (proj [_] projection)
  (joins [_] joins)
  (valAt [_ k]
    (str (when-not (empty? relation-alias) (str relation-alias  ".")) (name k))))

(defn project [relation columns]
  ;; TODO: join existing projected columns?
  (Relation. (base relation) (alt relation) (sel relation) columns (joins relation)))

(defn select [relation predicate]
  (Relation. (base relation) (alt relation) [:and (or (sel relation) [:= "1" "1"]) predicate] (proj relation) (joins relation)))

(defn join [relation join-relation join-predicate]
  ;; TODO: what checks are required on sels and projs?
  (Relation. (base relation) (alt relation) (sel relation) (proj relation) {:relation join-relation
                                                                            :predicate join-predicate}))

(defmulti to-sql type)
(defmethod to-sql String [relation] relation)
(defmethod to-sql Relation [relation]
  (let [raw-cols (proj relation)
        cols     (if (empty? raw-cols) ["*"] (map #(name %) raw-cols))
        alt      (alt relation)
        where    (sel relation)
        joins    (joins relation)]
          (wrap-parens (str "SELECT " (str/join ", " cols)
                            " FROM " (to-sql (base relation))
                            (when-not (empty? joins)
                              (str " JOIN " (to-sql (:relation joins)) " ON " (:predicate joins)))
                            (when-not (empty? alt)
                              (str " " alt))
                            (when-not (empty? where) (str " WHERE " (to-sql where)))))))
(defmethod to-sql clojure.lang.PersistentVector [predicate]
  (let [operator (first predicate)
        operands (vec (rest predicate))]
    (if (contains? #{:and :or} operator)
      (wrap-parens (str/join (str " " (str/upper-case (name operator)) " ") (map #(to-sql %) operands)))
      (wrap-parens (str/join " " [(to-sql (to-sql (first operands)))
                                  (name operator)
                                  (to-sql (last operands))])))))

(defn max [col]
  (str "max(" (name col) ")"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ps-person (Relation. "ps_person" "p" nil nil {}))
(def ps-names (Relation. "ps_names" "n" nil nil {}))
(to-sql ps-person)
(to-sql ps-names)

(def person-with-emplid-1878070 (select ps-person "emplid=1878070"))
(to-sql person-with-emplid-1878070)

(def dob-of-person-with-emplid-1878070 (project person-with-emplid-1878070 [(:dob ps-person)]))
(to-sql dob-of-person-with-emplid-1878070)

(to-sql (select ps-person (str "effdt <= " (to-sql (project ps-person [(max :effdt)])))))

;; (def outer-acad-prog {:base-relation "ps_acad_prog" :alias "ap"})
;; (def acad-prog {:base-relation "ps_acad_prog"})
;;
;; (def max-effdt-of-outer-acad-prog
;;   (project
;;     (select acad-prog (and (= (:emplid acad-prog) (:emplid outer-acad-prog))
;;                            (= (:acad_career acad-prog) (:acad_career outer-acad-prog))
;;                            (= (:stdnt_car_nbr ps-person) (:stndt_car_nbr outer-acad-prog))
;;                            (<= (:effdt ps-person) Date.)))
;;                            [(max :effdt)]))
;; (def max-effseq-of-max-effdt-of-ps-acad_prog
;;   (project
;;     (select acad-prog (and (= (:emplid acad-prog) (:emplid outer-acad-prog))
;;                            (= (:acad_career acad-prog) (:acad_career outer-acad-prog))
;;                            (= (:stdnt_car_nbr acad-prog) (:stndt_car_nbr outer-acad-prog))
;;                            (= (:effdt acad-prog) (:effdt outer-acad-prog))))
;;     [(max :effseq)]))
;; (def effectve-acad-progs
;;   (select outer-acad-prog (and (= :effdt max-effdt-of-outer-acad-prog)
;;                                (= :effseq max-effseq-of-max-effdt-of-ps-acad_prog))))
;;
;; (select effective-acad-progs (= :emplid "1878070"))
