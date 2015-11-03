(ns karmalack.slack
  (:require [karmalack.db :as db]
            [com.stuartsierra.component :as component]
            [slack-rtm.core :as rtm]
            [clojure.core.async :refer [go go-loop <!]]))


(def ^:private known-commands #{"inc" "dec" "skill" "banner"})
(def ^:private known-directed-commands #{"help"})

(defn- is-directed? [parts self-id]
  (let [cmd (first parts)]
    (or (= (str "<@" self-id ">") cmd)
        (= (str "<@" self-id ">:") cmd))))

(defn- parse-command [msg self-id]
  ;; A command could be a directed or a non-directed one
  ;; directed ones are the ones where karmabot was explicitly
  ;; asked for attention
  (let [parts (-> msg
                  clojure.string/trim
                  (clojure.string/split #" "))
        directed? (is-directed? parts self-id)
        command-name (if directed?
                       (second parts)
                       (first parts))]
    (if directed?
      (when (known-directed-commands command-name)
        {:directed? true
         :command   command-name
         :args      (vec (next (next parts)))})
      (when (known-commands command-name)
        {:directed? false
         :command   command-name
         :args      (vec (next parts))}))))

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

(defmethod handle-command "help" [_ db from _]
  (clojure.string/join
    "\n"
    ["```"
     "I accept the following commands:"
     ""
     "help                 : This command, needs to be directed to me."
     "inc @user            : Bump up the karma for @user."
     "dec @user            : Decrement @user's karma."
     "skill <skill string> : Set your skill/title for your karmalack profile page."
     "banner <https URL>   : Set your banner for your karmalack profile page."
     "```"]))

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
                          (println "command:" cmd)
                          (let [command (:command cmd)
                                args (:args cmd)
                                cmd-line (concat [command] args)]
                            (handle-command cmd-line database user channel))
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

(comment
  (defn start-debug [config]
    (let [sys (component/system-map
                :database (db/new-database config)
                :slackbot (component/using
                            (new-slackbot config)
                            [:database]))]
      (component/start sys)))

  (def sys (start-debug (karmalack.config/config)))

  (component/stop sys))
