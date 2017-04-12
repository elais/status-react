(ns status-im.commands.handlers.loading
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [path after dispatch subscribe trim-v debug register-sub]]
            [status-im.utils.handlers :as u]
            [status-im.utils.utils :refer [http-get show-popup]]
            [clojure.walk :as w]
            [clojure.string :as s]
            [status-im.data-store.commands :as commands]
            [status-im.components.status :as status]
            [status-im.utils.types :refer [json->clj]]
            [status-im.commands.utils :refer [reg-handler]]
            [status-im.constants :refer [console-chat-id wallet-chat-id]]
            [taoensso.timbre :as log]
            [status-im.utils.homoglyph :as h]
            [status-im.utils.js-resources :as js-res]))

(def commands-js "commands.js")

(defn fetch-group-chat-commands [app-db group-chat-id contacts-key]
      (let [contacts (get-in app-db [:chats group-chat-id :contacts])
            identities (mapv :identity contacts)
            my-contacts (mapv #(get contacts-key %) identities)]
        (doseq [contact my-contacts] (dispatch [::fetch-commands! contact]))))

(defn load-commands!
  [{:keys [current-chat-id contacts] :as db} [identity]]
  (let [identity (or identity current-chat-id)
        contact  (or (get contacts identity)
                     {:whisper-identity identity})
        group-chat? (subscribe [:group-chat?])]
    (when identity
      (if @group-chat?
        (fetch-group-chat-commands db identity contacts)
        (dispatch [::fetch-commands! contact])))))


(defn fetch-commands!
  [_ [{:keys [whisper-identity dapp? dapp-url name]}]]
  (cond
    (= console-chat-id whisper-identity)
    (dispatch [::validate-hash whisper-identity name js-res/console-js])

    (= wallet-chat-id whisper-identity)
    (dispatch [::validate-hash whisper-identity name js-res/wallet-js])

    (and dapp? dapp-url)
    (http-get (s/join "/" [dapp-url commands-js])
              (fn [response]
                (and
                  (string? (.text response))
                  (when-let [content-type (.. response -headers (get "Content-Type"))]
                    (s/includes? "application/javascript" content-type))))
              #(dispatch [::validate-hash whisper-identity name %])
              #(dispatch [::validate-hash whisper-identity name js-res/dapp-js]))

    :else
    (dispatch [::validate-hash whisper-identity name js-res/commands-js])))

(defn dispatch-loaded!
  [db [identity name file]]
  (if (::valid-hash db)
    (dispatch [::parse-commands! identity name file])
    (dispatch [::loading-failed! identity ::wrong-hash])))

(defn get-hash-by-identity
  [db identity]
  (get-in db [:contacts identity :dapp-hash]))

(defn get-hash-by-file
  [file]
  ;; todo tbd hashing algorithm
  (hash file))

(defn parse-commands! [_ [identity name file]]
  (status/parse-jail identity file
                     (fn [result]
                       (let [{:keys [error result]} (json->clj result)]
                         (log/debug "Error parsing commands: " error result)
                         (if error
                           (dispatch [::loading-failed! identity ::error-in-jail error])
                           (if identity
                             (dispatch [::add-commands identity name file result])
                             (dispatch [::add-all-commands result])))))))

(defn validate-hash
  [db [identity _ file]]
  (let [valid? true
        ;; todo check
        #_(= (get-hash-by-identity db identity)
             (get-hash-by-file file))]
    (assoc db ::valid-hash valid?)))

(defn mark-as [as coll]
  (->> coll
       (map (fn [[k v]] [k (assoc v :type as)]))
       (into {})))

(defn filter-forbidden-names [account id commands]
  (->> commands
       (remove (fn [[_ {:keys [registered-only]}]]
                 (and (not (:address account))
                      registered-only)))
       (remove (fn [[n]]
                 (and
                   (not= console-chat-id id)
                   (h/matches (name n) "password"))))
       (into {})))

(defn add-group-chat-command-owner-and-name
  [name id commands]
  (let [group-chat? (subscribe [:group-chat?])]
    (if @group-chat?
      (->> commands
           (map (fn [[k v]]
                  [k (assoc v
                            :command-owner (str id)
                            :group-chat-command-name (if name
                                                       (str name "/" (:name v))
                                                       (:name v)))]))
           (into {}))
      commands)))

(defn process-new-commands [account name id commands]
  (->> commands
       (filter-forbidden-names account id)
       (add-group-chat-command-owner-and-name name id)
       (mark-as :command)))
       
(defn add-commands
  [db [id name _ {:keys [commands responses autorun]}]]
  (let [account    @(subscribe [:get-current-account])
        commands'  (process-new-commands account name id commands)
        responses' (filter-forbidden-names account id responses)
        current-chat-id @(subscribe [:get-current-chat-id])
        current-commands (into {} (get-in db [current-chat-id :commands]))]
    (dispatch [:add-key-log db])
      (-> db
          (assoc-in [current-chat-id :commands] (conj current-commands commands'))
          (assoc-in [current-chat-id :responses] (mark-as :response responses'))
          (assoc-in [current-chat-id :commands-loaded] true)
          (assoc-in [current-chat-id :autorun] autorun))))

(defn save-commands-js!
  [_ [id _ file]]
  (commands/save {:chat-id id :file file}))

(defn loading-failed!
  [db [id reason details]]
  (let [url (get-in db [:chats id :dapp-url])]
    (let [m (s/join "\n" ["commands.js loading failed"
                          url
                          id
                          (name reason)
                          details])]
      (show-popup "Error" m)
      (log/debug m))))

(reg-handler :load-commands! (u/side-effect! load-commands!))
(reg-handler ::fetch-commands! (u/side-effect! fetch-commands!))

(reg-handler ::validate-hash
  (after dispatch-loaded!)
  validate-hash)

(reg-handler ::parse-commands! (u/side-effect! parse-commands!))

(reg-handler ::add-commands
  [(path :chats)
   (after save-commands-js!)
   (after #(dispatch [:check-autorun]))
   (after (fn [_ [id]]
            (dispatch [:invoke-commands-loading-callbacks id])
            (dispatch [:invoke-chat-loaded-callbacks id])))]
  add-commands)

(reg-handler ::add-all-commands
  (fn [db [{:keys [commands responses]}]]
    (assoc db :all-commands {:commands  (mark-as :command commands)
                             :responses (mark-as :response responses)})))

(reg-handler ::loading-failed! (u/side-effect! loading-failed!))

(reg-handler :add-commands-loading-callback
  (fn [db [chat-id callback]]
    (update-in db [::commands-callbacks chat-id] conj callback)))

(reg-handler :invoke-commands-loading-callbacks
  (u/side-effect!
    (fn [db [chat-id]]
      (let [callbacks (get-in db [::commands-callbacks chat-id])]
        (doseq [callback callbacks]
          (callback))
        (dispatch [::clear-commands-callbacks chat-id])))))

(reg-handler ::clear-commands-callbacks
  (fn [db [chat-id]]
    (assoc-in db [::commands-callbacks chat-id] nil)))
