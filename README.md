## Overview

`twitterproxy` is a Scala web application that acts as an authenticated server-side proxy to the [Twitter REST API][twitterapi].

Once configured with a key and secret to a registered twitter app, `twitterproxy` uses OAuth to sign in users with their twitter account. Once signed in, it can proxy requests to the twitter API on behalf of the signed in user.

Signing in results in an access token and secret for the user. This is encoded and set in a cookie which is then used when invoking the twitter API. This cookie is set on the root domain which means any app on that domain can use the proxy.

> Note that if you use the project as-is, the cookie will not be encrypted. Encoding the token and secret in plaintext is not recommended. Developers are encouraged to provide a strong implementation of the ``twitterproxy.Crypto`` interface to support encryption / decryption. Alternatively, the token and secret can be persisted on the server.

Once the user has signed in and the cookie has been set, all `GET` and `POST` requests to the `/proxy/*` URL will sent to `https://api.twitter.com/*` and the result will be returned as-is. For example, a request to `/proxy/1.1/search/tweets.json?q=test` will be sent to `https://api.twitter.com/1.1/search/tweets.json?q=test` and the resulting JSON response will be returned to the caller.

[twitterapi]: https://dev.twitter.com/docs/api/1.1

## Configuration

Configuration properties need to be specified in `src/main/resources/application.properties`:

    # twitter application key
    twitterproxy.app-key=
    # twitter application secret
    twitterproxy.app-secret=
    # twitter calls back this URL after authentication (http://<domain>[/<context>]/callback)
    # e.g. http://www.example.com/twitterproxy/callback
    twitterproxy.callback-url=

## Running

Once configured (set `twitterproxy.callback-url=http://localhost:8080/callback`), the app can be built and run using `sbt`. :

    ~ > sbt
    sbt> container:start

Go to `http://localhost:8080/`. You will be redirected to `http://localhost:8080/test` where you can test signing in and executing a couple of calls to the twitter api.

## Debugging

To easily run and debug the app in Eclipse and other IDEs you can run `runner.RunWebApp`. This starts an embedded Jetty server and mounts this webapp. Go to `http://localhost:8080/`

## Deployment

To package and deploy the generated `war`:

    sbt package

## Notes

* Uses the [Scalatra][scalatra] web framework.
* Uses [oauth-signpost][signpost] for signing OAuth requests.
* Uses [dispatch][dispatch] for async http calls to the twitter api

[scalatra]: http://www.scalatra.org/
[signpost]: https://code.google.com/p/oauth-signpost/
[dispatch]: http://dispatch.databinder.net/Dispatch.html