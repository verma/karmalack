(ns karmalack.slack
  (:require [karmalack.db :as db]
            [karmalack.slack-api :as sa]
            [com.stuartsierra.component :as component]
            [slack-rtm.core :as rtm]
            [clojure.core.async :as async :refer [go go-loop <!]]))


(def ^:private known-directed-commands #{:help :skill :banner})
(def ^:private reaction-mappings
  {"+1" :inc
   "-1" :dec})


(defn- starts-with? [text s]
  (zero? (.indexOf text s)))

(defn- parse-self [cmd self-id]
  (let [prefixes [(str "<@" self-id ">:")
                  (str "<@" self-id ">")]]
    (loop [[p & ps] prefixes]
      (if p
        (if (starts-with? cmd p)
          [true (clojure.string/trim (subs cmd (count p)))]
          (recur ps))
        [false cmd]))))

(defn- to-user [id channel msg]
  {:type    "message"
   :channel channel
   :text (str "<@" id ">: " msg)})

(defn- timestamp []
  (.getTime (java.util.Date.)))

(defn- formatted-timestamp []
  (let [formatter (java.text.SimpleDateFormat. "yyyy/MM/dd HH:mm:ss")]
    (.format formatter (java.util.Date.))))

(defmulti handle-command (fn [cmd _ _ _]
                           (:type cmd)))

(defn- user-url [base-url id]
  (str base-url "/#/users/" id))

(defmethod handle-command :inc [{:keys [from channel ts]} _ db sapi]
  (when-let [rm (sa/get-reaction-info sapi channel ts)]
    (when-not (= from (:user rm))
      (db/karma-inc! db (:user rm) from channel)
      (println ":: sb :: karma inc for" (:user rm))
      nil)))

(defmethod handle-command :dec [{:keys [from channel ts]} _ db sapi]
  (when-let [rm (sa/get-reaction-info sapi channel ts)]
    (when-not (= from (:user rm))
      (db/karma-dec! db (:user rm) from channel)
      (println ":: sb :: karma dec for" (:user rm))
      nil)))

(defmethod handle-command :banner [{:keys [from channel args]} config db _]
  (to-user
    from channel
    (if-let [r (re-matches #"^<(https:\/\/.*)>$" (or (first args) ""))]
      (do
        (db/settings-save-banner! db from (second r))
        (str
          "banner update :thumbsup:. See it here: "
          (user-url (:base-url config) from)))
      ":thumbsdown: banner command needs a https url as an argument.")))

(defmethod handle-command :skill [{:keys [args from channel]} config db _]
  (to-user
    from channel
    (if-let [s (seq args)]
      (let [skill (clojure.string/join " " s)]
        (db/settings-save-skill! db from skill)
        (str
          "skill updated :thumbsup:. See it here: "
          (user-url (:base-url config) from)))
      (do
        (db/settings-save-skill! db from "")
        "skill cleared :thumbsup:"))))

(defmethod handle-command :pong [{:keys [time]} _ _ _]
  (println ":: sb ::" (formatted-timestamp) ": got pong, round-trip time is" (- (timestamp) time) "ms"))

(defmethod handle-command :help [{:keys [from channel]} _ _ _]
  (to-user
    from channel
    (clojure.string/join
      "\n"
      ["```"
       "Just award messages either a thumbs up or a thumbs down reaction to contribute towards their karma."
       ""
       "Along with that, I accept the following commands which all need to be directed to me like @karmabot: command <args>:"
       ""
       "help                 : This command."
       "skill <skill string> : Set your skill/title for your karmalack profile page."
       "banner <https URL>   : Set your banner for your karmalack profile page."
       "```"])))

(defmethod handle-command :default [cmd _ _ _]
  (println ":: sb :: Unknown command:" cmd))

(defn- parse-command [self-id cmd]
  (case (:type cmd)
    "pong"
    (assoc cmd :type :pong)

    "reaction_added"
    (when-let [mapping (get reaction-mappings (:reaction cmd))]
      {:type    mapping
       :from    (:user cmd)
       :channel (get-in cmd [:item :channel])
       :ts (get-in cmd [:item :ts])})

    "message"
    (let [[directed? cmd-str] (parse-self (:text cmd) self-id)
          parts (clojure.string/split cmd-str #" ")
          cmd-name (keyword (first parts))
          args (next parts)]
      (when (and directed?
                 (known-directed-commands cmd-name))
        {:type    cmd-name
         :directed? directed?
         :args    (vec args)
         :channel (:channel cmd)
         :from    (:user cmd)}))
    nil))

(defn- process-cmd [cmd config database slack-api]
  (go
    (try
      (let [parsed-cmd (parse-command (:self-id config) cmd)
            res (handle-command parsed-cmd
                                config
                                database slack-api)]
        res)
      (catch Exception e
        (println ":: sb :: WARN: failed to process command:" cmd e)))))

(comment
  (def test-a {:type "reaction_added", :user "U02G768Q0", :item {:type "message", :channel "C0D88BQJU", :ts 1447009957.000002}, :reaction "+1", :event_ts 1447009988.945251})
  (def test-b {:type "message", :channel "C0D88BQJU", :user "U02G768Q0", :text "<@U0D835P61>: skill hello world", :ts 1447009972.000004, :team "T02G698S5"})
  (def test-c {:type "message", :channel "C0D88BQJU", :user "U02G768Q0", :text "what", :ts 1447009967.000003, :team "T02G698S5"})


  (let [self-id "U0D835P61"]
    (println (parse-command self-id test-a)
             (parse-command self-id test-b)
             (parse-command self-id test-c))))

(defrecord SlackBot [slack-bot-token base-url
                     chan-shutdown
                     chan-pong chan-msg chan-rec
                     connection database slack-api]
  component/Lifecycle
  (start [this]
    (if connection
      connection
      (let [conn (rtm/connect slack-bot-token)
            self-id (get-in conn [:start :self :id])
            self-name (get-in conn [:start :self :name])
            pub (:events-publication conn)
            chan-shutdown (async/chan)
            chan-pong (rtm/sub-to-event pub :pong)
            chan-msg (rtm/sub-to-event pub :message)
            chan-rec (rtm/sub-to-event pub :reaction_added)]
        (println ":: sb :: startup as:" self-name "and id:" self-id)

        ;; start a go look
        (go-loop []
          (let [to-chan (async/timeout 10000)]
            (when-let [[cmd ch] (async/alts!
                                  [chan-shutdown chan-msg chan-rec
                                   chan-pong to-chan])]
              (cond
                ;; Do nothing when a shutdown has been triggered
                (= ch chan-shutdown)
                nil

                ;; no activity just send a ping to the server
                (= ch to-chan)
                (do
                  (rtm/send-event (:dispatcher conn) {:type "ping"
                                                      :time (timestamp)})
                  (recur))

                ;; all other commands go through processing pipeline
                :else
                (do
                  (go
                    (println ":: sb :: " cmd)
                    (when-let [r (<! (process-cmd cmd
                                                  {:self-id  self-id
                                                   :base-url base-url}
                                                  database slack-api))]
                      (rtm/send-event (:dispatcher conn) r)))
                  (recur))))))

        (assoc this :connection conn
                    :chan-shutdown chan-shutdown
                    :chan-pong chan-pong
                    :chan-msg chan-msg
                    :chan-rec chan-rec))))

  (stop [this]
    (if connection
      (do
        (println ":: sb :: shutdown.")
        (async/close! chan-shutdown)
        (rtm/unsub-from-event (:events-publication connection)
                              :pong chan-pong)
        (rtm/unsub-from-event (:events-publication connection)
                              :message chan-msg)
        (rtm/unsub-from-event (:events-publication connection)
                              :reaction_added chan-rec)
        (assoc this :connection nil
                    :chan-shutdown nil
                    :chan-pong nil
                    :chan-msg nil
                    :chan-rec nil))
      this)))

(defn new-slackbot [config]
  (map->SlackBot config))

(comment
  (defn start-debug [config]
    (let [sys (component/system-map
                :database (db/new-database config)
                :slack-api (sa/new-slack-api config)
                :slackbot (component/using
                            (new-slackbot config)
                            [:database :slack-api]))]
      (component/start sys)))

  (def sys (start-debug (karmalack.config/config)))

  (component/stop sys))
