(ns clj-jwt.core
  (:require
    [clj-jwt.base64      :refer [url-safe-encode-str url-safe-decode-str]]
    [clj-jwt.sign        :refer [get-signature-fn get-verify-fn supported-algorithm?]]
    [clj-jwt.intdate     :refer [joda-time->intdate]]
    [clj-jwt.json-key-fn :refer [write-key read-key]]
    [clojure.data.json   :as json]
    [clojure.string      :as str])
  (:import
   (java.util UUID)))

(def ^:private DEFAULT_SIGNATURE_ALGORITHM :HS256)

(defn write-json-val [key value]
  (if (and (= :uuid key)
           (instance? UUID value))
    (-> ^UUID value .toString)
    value))

(defn read-json-value [key value]
  (if (= :uuid key)
    (UUID/fromString value)
    value))

(def ^:private map->encoded-json (comp url-safe-encode-str
                                       #(json/write-str % :key-fn write-key :value-fn write-json-val)))

(def ^:private encoded-json->map (comp #(json/read-str % :key-fn read-key :value-fn read-json-value)
                                       url-safe-decode-str))
(defn- update-map [m k f] (if (contains? m k) (update-in m [k] f) m))

(defrecord JWT [header claims signature encoded-data])

; ----------------------------------
; JsonWebToken
; ----------------------------------
(defprotocol JsonWebToken
  "Protocol for JsonWebToken"
  (init           [this claims] "Initialize token")
  (encoded-header [this] "Get url-safe base64 encoded header json")
  (encoded-claims [this] "Get url-safe base64 encoded claims json")
  (to-str         [this] "Generate JsonWebToken as string"))

(extend-protocol JsonWebToken
  JWT
  (init [this claims]
    (let [claims (reduce #(update-map % %2 joda-time->intdate) claims [:exp :nbf :iat])]
      (assoc this :header {:alg "none" :typ "JWT"} :claims claims :signature "")))

  (encoded-header [this]
    (-> this :header map->encoded-json))

  (encoded-claims [this]
    (-> this :claims map->encoded-json))

  (to-str [this]
    (str (encoded-header this) "." (encoded-claims this) "." (get this :signature ""))))


; ----------------------------------
; JsonWebSignature
; ----------------------------------
(defprotocol JsonWebSignature
  "Protocol for JonWebSignature"
  (set-alg [this alg] "Set algorithm name to JWS Header Parameter")
  (set-key-id [this key-id] "Set Key ID to JWS Header Parameter")
  (sign    [this key] [this alg key] [this alg key-id key] "Set signature to this token")
  (verify  [this] [this key] [this algorithm key] "Verify this token"))

(extend-protocol JsonWebSignature
  JWT
  (set-alg [this alg]
    (assoc-in this [:header :alg] (name alg)))

  (set-key-id [this key-id]
    (assoc-in this [:header :kid] key-id))

  (sign
    ([this key] (sign this DEFAULT_SIGNATURE_ALGORITHM key))
    ([this alg key] (sign this alg nil key))
    ([this alg key-id key]
     (let [this*   (cond-> this
                     alg    (set-alg alg)
                     key-id (set-key-id key-id))
           sign-fn (get-signature-fn alg)
           data    (str (encoded-header this*) "." (encoded-claims this*))]
       (assoc this* :signature (sign-fn key data) :encoded-data data))))

  (verify
    ([this] (verify this ""))
    ([this key]
     (let [alg (-> this :header :alg keyword)]
       (cond
         (= :none alg) (= "" key (:signature this))

         (supported-algorithm? alg)
         (let [verify-fn (get-verify-fn alg)]
           (verify-fn key (:encoded-data this) (:signature this)))

         :else (throw (Exception. "Unkown signature")))))
    ([this algorithm key]
     (if (= algorithm (-> this :header :alg keyword))
       (verify this key)
       false))))

; =jwt
(defn jwt [claim] (init (->JWT "" "" "" "") claim))

; =str->jwt
(defn str->jwt
  "Decode a string into a JWT"
  [jwt-string]
  (let [[header claims signature] (str/split jwt-string #"\.")]
    (map->JWT {:header       (encoded-json->map header)
               :claims       (encoded-json->map claims)
               :signature    (or signature "")
               :encoded-data (str header "." claims)})))
