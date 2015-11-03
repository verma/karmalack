(ns karmalack.db
  (:require [clj-time.core :as t]
            [clj-time.local :as l]
            [clj-time.coerce :as c]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defrecord Database [datomic-uri connection]
  component/Lifecycle
  (start [this]
    ;; initialize our database
    (println ":: db :: startup with:" datomic-uri)
    (if connection
      this
      (assoc this :connection (d/connect datomic-uri))))

  (stop [this]
    (if-not connection
      this
      (do
        (println ":: db :: shutdown.")
        (assoc this :connection nil)))))


(defn- find-user-by-id [conn id]
  (let [db (d/db conn)]
    (:db/id (d/entity db [:user/userid id]))))

(defn- add-user [conn id]
  (let [r @(d/transact
             conn
             [{:db/id (d/tempid :db.part/user)
               :user/userid id
               :user/skill ""
               :user/banner ""}])]
    (-> r :tempids vals first)))

(defn- user-entity [conn userid]
  (if-let [u (find-user-by-id conn userid)]
    u
    (add-user conn userid)))

(defn- karma-delta [conn delta userid from source]
  (let [id (user-entity conn userid)]
    @(d/transact
       conn
       [{:db/id        (d/tempid :db.part/user)
         :karma/user   id
         :karma/grantee from
         :karma/source source
         :karma/delta  delta
         :karma/ts     (java.util.Date.)}])))

(defn karma-inc! [component userid from source]
  (karma-delta (:connection component) 1 userid from source))

(defn karma-dec! [component userid from source]
  (karma-delta (:connection component) -1 userid from source))

(defn settings-save-skill! [component userid skill]
  @(d/transact
     (:connection component)
     [{:db/id       (d/tempid :db.part/user)
       :user/userid userid
       :user/skill  skill}]))

(defn settings-save-banner! [component userid banner]
  @(d/transact
     (:connection component)
     [{:db/id       (d/tempid :db.part/user)
       :user/userid  userid
       :user/banner  banner}]))

(defn karma-stats [component]
  (let [totals (into {}
                     (d/q '[:find ?userid (sum ?delta)
                            :with ?k
                            :where [?u :user/userid ?userid]
                            [?k :karma/user ?u]
                            [?k :karma/delta ?delta]]
                          (d/db (:connection component))))]
    totals))

(defn all-user-settings [component]
  (->>
    (d/q
      '[:find ?userid ?skill ?banner
        :where
        [?u :user/userid ?userid]
        [?u :user/skill ?skill]
        [?u :user/banner ?banner]]
      (d/db (:connection component)))
    (map (fn [[id skill banner]]
           [id {:skill skill :banner banner}]))
    (into {})))

(defn- user-stats [db id]
  (->>
    (d/q '[:find ?source ?grantee ?delta
           :in $ ?id
           :with ?k
           :where
           [?k :karma/delta ?delta]
           [?k :karma/user ?u]
           [?k :karma/source ?source]
           [?k :karma/grantee ?grantee]
           [?u :user/userid ?id]]
         db id)))

(defn- user-info [db id]
  (some->>
    (d/q '[:find ?banner ?skill
           :in $ ?id
           :where
           [?u :user/userid ?id]
           [?u :user/skill ?skill]
           [?u :user/banner ?banner]]
         db id)
    (map #(zipmap [:banner :skill] %))
    first))

(defn all-user-stats [component id]
  (let [db (d/db (:connection component))]
    (when-let [ui (user-info db id)]
      (assoc ui :stats (user-stats db id)))))

(defn setup-database! [uri]
  (d/create-database uri)
  @(d/transact
     (d/connect uri)
     '[
       ;; User
       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/userid
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/unique             :db.unique/identity
        :db/doc                "The user's id"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/skill
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The user's skill string"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/banner
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The banner image URL for the user"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/karma
        :db/valueType          :db.type/ref
        :db/cardinality        :db.cardinality/many
        :db/doc                "User's karma"
        :db.install/_attribute :db.part/db}


       ;; Karma
       ;;
       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/user
        :db/valueType          :db.type/ref
        :db/cardinality        :db.cardinality/one
        :db/doc                "The user's id"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/source
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The channel where this karma was awarded"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/grantee
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The user who granted this karma point"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/delta
        :db/valueType          :db.type/long
        :db/cardinality        :db.cardinality/one
        :db/doc                "The amount of karma awarded, positive or negative"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/ts
        :db/valueType          :db.type/instant
        :db/cardinality        :db.cardinality/one
        :db/doc                "The timestamp when this karma was awarded"
        :db.install/_attribute :db.part/db}
       ]))

(defn cleanup! [uri]
  (d/delete-database uri))

(defn reset-database! []
  (let [uri "datomic:free://localhost:4334/karmalack1"]
    (cleanup! uri)
    (setup-database! uri)))

(defn new-database [config]
  (map->Database config))

