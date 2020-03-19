# clj-jwt

A Clojure library for JSON Web Token(JWT) [draft-ietf-oauth-json-web-token-19](http://tools.ietf.org/html/draft-ietf-oauth-json-web-token-19)

## Supporting algorithms
 * HS256, HS384, HS512
 * RS256, RS384, RS512
 * ES256, ES384, ES512

## Not supporting
 * JSON Web Encryption (JWE)

## Usage

### Leiningen

```clojure
[com.nedap.staffing-solutions/clj-jwt "0.2.0-alpha3"]
```

### Generate

```clojure
(ns foo
  (:require
    [clj-jwt.core  :refer :all]
    [clj-jwt.key   :refer [private-key]]
    [clj-time.core :refer [now plus days]]))

(def claim
  {:iss "foo"
   :exp (plus (now) (days 1))
   :iat (now)})

(def rsa-prv-key (private-key "rsa/private.key" "pass phrase"))
(def ec-prv-key  (private-key "ec/private.key"))

;; plain JWT
(-> claim jwt to-str)

;; HMAC256 signed JWT
(-> claim jwt (sign :HS256 "secret") to-str)

;; RSA256 signed JWT
(-> claim jwt (sign :RS256 rsa-prv-key) to-str)

;; ECDSA256 signed JWT
(-> claim jwt (sign :ES256 ec-prv-key) to-str)
```

### Verify

```clojure
(ns foo
  (:require
    [clj-jwt.core  :refer :all]
    [clj-jwt.key   :refer [private-key public-key]]
    [clj-time.core :refer [now plus days]]))

(def claim
  {:iss "foo"
   :exp (plus (now) (days 1))
   :iat (now)})

(def rsa-prv-key (private-key "rsa/private.key" "pass phrase"))
(def rsa-pub-key (public-key  "rsa/public.key"))
(def ec-prv-key  (private-key "ec/private.key"))
(def ec-pub-key  (public-key  "ec/public.key"))

;; verify plain JWT
(let [token (-> claim jwt to-str)]
  (-> token str->jwt verify))

;; verify HMAC256 signed JWT
(let [token (-> claim jwt (sign :HS256 "secret") to-str)]
  (-> token str->jwt (verify "secret")))

;; verify RSA256 signed JWT
(let [token (-> claim jwt (sign :RS256 rsa-prv-key) to-str)]
  (-> token str->jwt (verify rsa-pub-key)))

;; verify ECDSA256 signed JWT
(let [token (-> claim jwt (sign :ES256 ec-prv-key) to-str)]
  (-> token str->jwt (verify ec-pub-key)))
```

You can specify algorithm name (OPTIONAL) for more secure verification.

```clj
(ns foo
  (:require
    [clj-jwt.core  :refer :all]))

;; verify with specified algorithm
(let [key   "secret"
      token (-> {:foo "bar"} jwt (sign :HS256 key) to-str)]
  (-> token str->jwt (verify :HS256 key)) ;; => true
  (-> token str->jwt (verify :none key))) ;; => false
```

### Decode

```clj
(ns foo
  (:require
    [clj-jwt.core  :refer :all]))

(def claim
  {:iss "foo"
   :exp (plus (now) (days 1))
   :iat (now)})

;; decode plain JWT
(let [token (-> claim jwt to-str)]
  (println (-> token str->jwt :claims)))

;; decode signed JWT
(let [token (-> claim jwt (sign :HS256 "secret") to-str)]
  (println (-> token str->jwt :claims)))
```

## License

Copyright © 2015 [uochan](http://twitter.com/uochan)

Distributed under the Eclipse Public License, the same as Clojure.
