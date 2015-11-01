(ns karmalack.slack
  (:require [karmalack.db :as db]
            [com.stuartsierra.component :as component]
            [slack-rtm.core :as rtm]
            [clojure.core.async :refer [go go-loop <!]]))


(defn starts-with [s ss]
  (not (neg? (.indexOf s ss))))


(defn- parse-command [msg self-id]
  (when-let [[_ id text] (re-matches #"^<@([A-Z0-9]+)>:?(.*)" msg)]
    (when (= id self-id)
      (clojure.string/split
        (clojure.string/trim text) #" "))))

(defn- validate-user [tag]
  (if-let [v (and (not (nil? tag))
                  (when-let [r (re-matches #"^<@([A-Z0-9]+)>" tag)]
                    (second r)))]
    v
    (throw (Exception. ":thumbsdown: a valid user id is needed as an argument, e.g. @user."))))


(defn- to-user [id msg]
  (str "<@" id ">: " msg))

(defmulti handle-command (fn [cmd db from channel]
                           (first cmd)))

(defmethod handle-command "inc" [[_ user] db from channel]
  (let [u (validate-user user)]
    (when (= u from)
      (throw (Exception. ":thumbsdown: you cannot assign karma to yourself.")))
    (db/karma-inc! db u from channel)
    ":thumbsup:"))

(defmethod handle-command "dec" [[_ user] db from channel]
  (let [u (validate-user user)]
    (when (= u from)
      (throw (Exception. ":thumbsdown: you cannot assign karma to yourself.")))
    (db/karma-dec! db u from channel)
    ":thumbsup:"))

(defmethod handle-command "banner" [[_ url] db from _]
  (if-let [r (re-matches #"^<(https:\/\/.*)>$" (or url ""))]
    (do
      (db/settings-save-banner! db from (second r))
      (str "banner update :thumbsup:"))
    (throw (Exception. ":thumbsdown: banner command needs a https url as an argument."))))

(defmethod handle-command "skill" [[_ s & parts] db from _]
  (if (clojure.string/blank? s)
    (do
      (db/settings-save-skill! db from "")
      "skill cleared :thumbsup:")
    (let [skill (clojure.string/join
                  " "
                  (concat [s] parts))]
      (db/settings-save-skill! db from skill)
      "skill updated :thumbsup:")))

(defmethod handle-command :default [_ _ _ _]
  (str "Sorry I don't understand that command, known commands: inc, dec, banner and skill."))

(defrecord SlackBot [slack-bot-token connection chan database]
  component/Lifecycle
  (start [this]
    (if connection
      connection
      (let [conn (rtm/connect slack-bot-token)
            self-id (get-in conn [:start :self :id])
            self-name (get-in conn [:start :self :name])
            chan (rtm/sub-to-event (:events-publication conn) :message)]
        (println ":: sb :: startup as:" self-name "and id:" self-id)

        ;; start a go look
        (go-loop []
          (when-let [{:keys [channel user text]} (<! chan)]
            (println ":: sb :: " channel user text)

            (when (and channel user text)
              (when-let [cmd (parse-command text self-id)]
                (let [r (try
                          (handle-command cmd database user channel)
                          (catch Exception e
                            (.getMessage e)))]
                  (rtm/send-event
                    (:dispatcher conn)
                    {:type    "message"
                     :channel channel
                     :text    (to-user user r)}))))
            (recur)))

        (assoc this :connection conn
                    :chan chan))))

  (stop [this]
    (if connection
      (do
        (println ":: sb :: shutdown.")
        (rtm/unsub-from-event (:events-publication connection) :message chan)
        (assoc this :connection nil :chan nil))
      this)))

(defn new-slackbot [config]
  (map->SlackBot config))

