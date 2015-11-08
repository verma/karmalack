(ns karmalack.slack-api
  (:require [com.stuartsierra.component :as component]
            [clj-slack.core :as slack]
            [clojure.core.cache :as cache]
            [clojure.core.async :as async :refer [go <!]]))

(defrecord SlackAPI [slack-api-token slack-cache-ttl cache]
  component/Lifecycle
  (start [this]
    (if (and cache slack-api-token)
      this
      (assoc this :slack-api-token slack-api-token
                  :cache (atom (cache/ttl-cache-factory {} :ttl (or slack-cache-ttl
                                                                    20000))))))
  (stop [this]
    (if (and cache slack-api-token)
      (dissoc this :cache :slack-api-token)
      this)))

(defn new-slack-api [config]
  (map->SlackAPI config))

(defn- slack-request
  ([token request]
    (slack-request token request {}))
  ([token request params]
   (let [conn {:api-url "https://slack.com/api"
               :token   token}]
     (slack/slack-request conn request params))))

(defn- cache-op [cache-atom key f]
  (let [cache @cache-atom
        new-cache (if (cache/has? cache key)
                    (cache/hit cache key)
                    (cache/miss cache
                                key (f)))]
    (get (reset! cache-atom new-cache) key)))

(defn get-all-channels [{:keys [slack-api-token cache]}]
  (cache-op cache :channels
            (fn []
              (->> (slack-request slack-api-token
                                  "channels.list")
                   :channels
                   (map #(vector (:id %) (:name %)))
                   (into {})))))

(defn get-all-users [{:keys [slack-api-token cache]}]
  (cache-op cache :users
            (fn []
              (->> (slack-request slack-api-token
                                  "users.list" {"presence" "1"})
                   :members
                   (map #(vector (:id %) {:name (:name %)
                                          :presence (:presence %)
                                          :avatar (or (get-in % [:profile :image_192])
                                                      (get-in % [:profile :image_72]))
                                          :avatar-small (or (get-in % [:profile :image_48])
                                                            (get-in % [:profile :image_32]))}))
                   (into {})))))

(defn get-team-info [{:keys [:slack-api-token :cache]}]
  (cache-op cache :team
            (fn []
              (let [info
                    (-> (slack-request slack-api-token
                                       "team.info")
                        :team)]
                (-> info
                    (select-keys [:id :name])
                    (assoc :avatar (or (get-in info [:icon :image_132])
                                       (get-in info [:icon :image_102])
                                       (get-in info [:icon :image_88]))))))))

(defn get-reaction-info [{:keys [:slack-api-token :cache]} channel ts]
  (cache-op cache (keyword (str "reaction-" channel "-" ts))
            (fn []
              (let [r (-> (slack-request slack-api-token
                                         "reactions.get"
                                         {"channel" channel
                                          "timestamp" ts})
                          :message)]
                r))))
