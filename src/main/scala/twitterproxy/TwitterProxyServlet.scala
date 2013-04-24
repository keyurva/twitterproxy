package twitterproxy

import org.scalatra._
import _root_.akka.dispatch._
import _root_.akka.actor.ActorSystem

class TwitterProxyServlet extends ScalatraServlet with FutureSupport with CorsSupport {
  import TwitterProxyServlet._
  
  implicit lazy val ctx = servletContext(TwitterProxyContextAttributeName).asInstanceOf[TwitterProxyContext]
  implicit lazy val crypto = servletContext(TwitterProxyCryptoAttributeName).asInstanceOf[Crypto]
  val system = ActorSystem()
  implicit def executor = system.dispatcher
  
  notFound {
    if(request.requestMethod == Options)
      response.setHeader("Access-Control-Allow-Headers", request.getHeader("Access-Control-Request-Headers"))
    else
      redirect("/test")
  }
  
  get("/test") {
    val template = io.Source.fromURL(Thread.currentThread.getContextClassLoader.getResource("test.html")) mkString
    
    contentType = "text/html;charset=utf-8"
    template.replace("${contextPath}", request.getContextPath)
  }
  
  getOrPost("/proxy/*") {
    
    cookies.get(AccessTokenCookieName) match {
      case Some(accessTokenCookie) =>        
        val prom = Promise[String]()
        
        val path = "/" + multiParams("splat").mkString("/")
        val accessToken = TokenSecretPair.decrypt(accessTokenCookie)
        
        TwitterProxy.proxyRequest(accessToken, path, request.requestMethod == Post, params) onComplete {
          case Right(StringHttpResponse(code, ctype, body)) =>
            status = code
            contentType = ctype
            prom.complete(Right(body))
        }
        
        prom.future
      case _ =>
        contentType = "application/json;charset=utf-8"
        jsonp(NotSignedInJson, params.get(CallbackParamName))
    }  
        
  }
  
  get("/callback") {
    (params.get(OAuthVerifierParamName), cookies.get(RequestTokenCookieName)) match {
      case (Some(verifier), Some(reqTokenCookie)) =>
        // decrypt request token
        val reqToken = TokenSecretPair.decrypt(reqTokenCookie)
        
        // get access token
        val accessToken = TwitterProxy.getAccessToken(reqToken, verifier)
        
        // encrypt and set access token cookie
        cookies.update(AccessTokenCookieName, TokenSecretPair.encrypt(accessToken))(cookieOpts(Int.MaxValue))
        
        // delete request token cookie
        cookies.delete(RequestTokenCookieName)(cookieOpts(0))
        
        // redirect to app URL if set, else simply return a "logged in" message
        cookies.get(AppRedirectUrlCookieName) match {
          case Some(redirectUrl) =>
            // delete cookie and redirect
            cookies.delete(AppRedirectUrlCookieName)(cookieOpts(0))
            redirect(redirectUrl)
          case _ => 
            contentType = "application/json;charset=utf-8"
            SignedInJson
        }
      case _ =>
        // redirect to app URL if set, else return a "not logged in" error
        cookies.get(AppRedirectUrlCookieName) match {
          case Some(redirectUrl) =>
            // delete cookie and redirect - should we return any error message?
            cookies.delete(AppRedirectUrlCookieName)(cookieOpts(0))
            redirect(redirectUrl)
          case _ => 
            contentType = "application/json;charset=utf-8"
            halt(401, NotSignedInJson)
        }
    }
  }
  
  get("/signin") {    
    // get request token
    val requestTokenResponse = TwitterProxy.getRequestToken
    
    // delete access token cookie if forced login
    if(params.get(ForceLoginParamName) == Some("true"))
      cookies.delete(AccessTokenCookieName)(cookieOpts(0))
    
    // encrypt and set request token cookie
    cookies.update(RequestTokenCookieName, TokenSecretPair.encrypt(requestTokenResponse.token))(cookieOpts(10 * 60))
    
    // set app redirect URL cookie to "redirect" param if specified or to "Referer" header
    // delete cookie if neither are found
    params.get(AppRedirectUrlParamName) match {
      case Some(redirect) => cookies.update(AppRedirectUrlCookieName, redirect)(cookieOpts(10 * 60))
      case _ => request.headers.get(RefererHeaderName) match {
        case Some(redirect) => cookies.update(AppRedirectUrlCookieName, redirect)(cookieOpts(10 * 60))
        case _ => cookies.delete(AppRedirectUrlCookieName)(cookieOpts(0))
      }
    }
    
    // redirect to authorization URL
    val authUrl = params.get(ForceLoginParamName) match {
      case Some("true") => "%s&%s=true".format(requestTokenResponse.authorizationUrl, ForceLoginParamName)
      case _ => requestTokenResponse.authorizationUrl
    }
    redirect(authUrl)
  }
  
  def cookieOpts(maxAge: Int) = CookieOptions().copy(path = "/", maxAge = maxAge)
  
  def getOrPost(transformer: RouteTransformer)(action: => Any) = {
    get(transformer)(action)
    post(transformer)(action)
  }
  
  def jsonp(json: String, callback: Option[String]) = callback match {
    case Some(cb) => "%s(%s);".format(cb, json)
    case _ => json
  }  
}

object TwitterProxyServlet {
  val TwitterProxyContextAttributeName = "twitterproxy.context"
  val TwitterProxyCryptoAttributeName = "twitterproxy.crypto"
  val RequestTokenCookieName = "tp_rt"
  val AccessTokenCookieName = "tp_at"
  val OAuthVerifierParamName = "oauth_verifier"
  val AppRedirectUrlCookieName = "tp_ru"
  val AppRedirectUrlParamName = "redirect_uri"
  val RefererHeaderName = "Referer"
  val NotSignedInJson = """{"signedIn" : false}"""
  val SignedInJson = """{"signedIn" : true}"""
  val ForceLoginParamName = "force_login"
  val CallbackParamName = "callback"
}
