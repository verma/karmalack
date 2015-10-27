(ns karmalack.slack
  (:require [karmalack.config :as config]
            [slack-rtm.core :as rtm]
            [clojure.core.async :refer [go go-loop <!]]))


(def conn (rtm/connect (:slack-bot-token (config/config))))

(def events-chan (:events-publication conn))
(def dispatcher (:dispatcher conn))

(def self-id (get-in conn [:start :self :id]))
(def self-name (get-in conn [:start :self :name]))

(defn starts-with [s ss]
  (not (neg? (.indexOf s ss))))


(defn parse-command [msg]
  (when-let [[_ id text] (re-matches #"^<@([A-Z0-9]+)>:?(.*)" msg)]
    (when (= id self-id)
      (clojure.string/split
        (clojure.string/trim text) #" "))))

(defn validate-user [tag]
  (if-let [v (and (not (nil? tag))
                  (when-let [r (re-matches #"^<@([A-Z0-9]+)>" tag)]
                    (second r)))]
    v
    (throw (Exception. "A valid user id is needed as an argument, e.g. @user."))))


(defn- to-user [id msg]
  (str "<@" id ">: " msg))

(defmulti handle-command first)

(defmethod handle-command "inc" [[_ user]]
  (let [u (validate-user user)]
    (str "going to inc karma: @" u)))

(defmethod handle-command "dec" [[_ user]]
  (let [u (validate-user user)]
    (str "going to dec karma: @" u)))

(defmethod handle-command "banner" [[_ url]]
  (if-let [r (re-matches #"^<(https:\/\/.*)>$" (or url ""))]
    (str "your banner has been updated: " (second r))
    (throw (Exception. "banner command needs a https url as a argument."))))

(defmethod handle-command "skill" [[_ s & parts]]
  (if (clojure.string/blank? s)
    "your skill has been cleared"
    (str
      "your skill has been updated to: " (clojure.string/join
                                           " "
                                           (concat [s] parts)))))

(defmethod handle-command :default [_]
  (str "Sorry I don't understand that command, known commands: inc, dec"))

(defn dispatch-handler [cmd & args])

(rtm/sub-to-event events-chan
                  :message
                  (fn [{:keys [channel user text]}]
                    (println channel user text)
                    (when-let [cmd (parse-command text)]
                      (let [r (try
                                (handle-command cmd)
                                (catch Exception e
                                  (.getMessage e)))]
                        (rtm/send-event
                          dispatcher
                          {:type    "message"
                           :channel channel
                           :text    (to-user user r)})))))
