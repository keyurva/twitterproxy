package twitterproxy

import oauth.signpost.OAuthConsumer
import oauth.signpost.basic.DefaultOAuthConsumer
import oauth.signpost.OAuthProvider
import oauth.signpost.basic.DefaultOAuthProvider
import oauth.signpost.http.HttpRequest
import com.ning.http.client.RequestBuilder

object TwitterProxy {
  def proxyRequest(
      accessToken: TokenSecretPair, 
      path: String, 
      post: Boolean,
      params: Map[String, String])
      (implicit ctx: TwitterProxyContext): dispatch.Promise[StringHttpResponse] = {
    
    import dispatch._
    import HttpClient._
    
    val uri = TwitterProxyConfig.ApiBaseUrl + path
    
    val hreq = newHttpRequest(uri, post, params, Map.empty)
    val (c, p) = (ctx.consumer, ctx.provider)
    c.setTokenWithSecret(accessToken.token, accessToken.secret)
    c.sign(hreq)
    
    val req = hreq.unwrap.asInstanceOf[RequestBuilder]
    
    Http(req > AsStringHttpResponse)
  }
  
  def getAccessToken(requestToken: TokenSecretPair, verifier: String)(implicit ctx: TwitterProxyContext) = {
    val (c, p) = (ctx.consumer, ctx.provider)
    c.setTokenWithSecret(requestToken.token, requestToken.secret)
    p.retrieveAccessToken(c, verifier)
    TokenSecretPair(c.getToken, c.getTokenSecret)
  }
  
  def getRequestToken()(implicit ctx: TwitterProxyContext): RequestTokenResponse = {
    val (c, p) = (ctx.consumer, ctx.provider)
    val url = p.retrieveRequestToken(c, ctx.config.CallbackUrl)
    RequestTokenResponse(TokenSecretPair(c.getToken, c.getTokenSecret), url)
  }
}

trait Crypto {
  def encrypt(input: String): String
  def decrypt(input: String): String
}

case class TokenSecretPair(token: String, secret: String)

object TokenSecretPair {
  def encrypt(pair: TokenSecretPair)(implicit crypto: Crypto): String = crypto.encrypt(pair.token + ":" + pair.secret)
  
  def decrypt(encrypted: String)(implicit crypto: Crypto): TokenSecretPair = {
    val decrypted = crypto.decrypt(encrypted)
    val Array(token, secret) = decrypted.split(":")
    TokenSecretPair(token, secret)
  }
}

case class RequestTokenResponse(token: TokenSecretPair, authorizationUrl: String)

case class StringHttpResponse(statusCode: Int, contentType: String, body: String)

case class TwitterProxyContext(config: TwitterProxyConfig) {
  def consumer: OAuthConsumer = new DefaultOAuthConsumer(config.AppKey, config.AppSecret)
  def provider: OAuthProvider = {
    import TwitterProxyConfig._
    val p = new DefaultOAuthProvider(RequestTokenUrl, AccessTokenUrl, AuthorizationUrl)
    p.setOAuth10a(true)
    p
  }
}

trait TwitterProxyConfig {    
  val AppKey: String
  val AppSecret: String
  val CallbackUrl: String
}

object TwitterProxyConfig {
  val ApiBaseUrl = "https://api.twitter.com"
  val RequestTokenUrl = ApiBaseUrl + "/oauth/request_token"
  val AccessTokenUrl = ApiBaseUrl + "/oauth/access_token"
  val AuthorizationUrl = ApiBaseUrl + "/oauth/authorize"
}

object HttpClient {
  val AsStringHttpResponse = dispatch.as.Response { r =>    
    val ctype = Option(r.getHeader("Content-Type")).getOrElse("")
    StringHttpResponse(r.getStatusCode, ctype, r.getResponseBody)
  }
  
  def newHttpRequest(uri: String, post: Boolean = false, params: Map[String, String], headers: Map[String, String]): HttpRequest =
    new HttpRequestAdapter(uri, post, params, headers)
  
  private[this] class HttpRequestAdapter(uri: String, post: Boolean = false, params: Map[String, String] = Map.empty, headers: Map[String, String] = Map.empty) extends HttpRequest {
    
    private[this] var u = 
      if(params.isEmpty || post) uri else "%s?%s".format(uri, queryString)
    
    private[this] val payload = 
      if(!post || params.isEmpty) null else new java.io.ByteArrayInputStream(queryString.getBytes)
    
    private[this] val hs = new collection.mutable.ListMap[String, String]
    hs ++= headers
    if(post) hs += "Content-Type" -> "application/x-www-form-urlencoded"
    
    private[this] def queryString = {
      import java.net.URLEncoder
      def enc(s: String) = URLEncoder.encode(s, "utf8")        
      params.map { case (n, v) => "%s=%s".format(enc(n), enc(v)) } mkString "&"
    }
    
    override def getMethod = if(post) "POST" else "GET"
    
    override def getRequestUrl = u
    override def setRequestUrl(url: String) = this.u = url
    
    override def setHeader(name: String, value: String) = hs += name -> value
    override def getHeader(name: String) = hs.getOrElse(name, "")
    override def getAllHeaders = collection.JavaConverters.mutableMapAsJavaMapConverter(hs).asJava
    
    override def getMessagePayload = payload
    
    override def getContentType = getHeader("Content-Type")
    
    override def unwrap = {
      def newRequest(uri: String, post: Boolean = false, params: Map[String, String] = Map.empty, headers: Map[String, String] = Map.empty): RequestBuilder = {
        import dispatch._
        
        val Some(req) = for {
          u <- Some(url(uri))
          m <- Some(if(post) u.POST else u)
          p <- Some(if(params.isEmpty) m else { if(post) m << params else m <<? params })
          h <- Some(if(headers.isEmpty) p else p <:< headers)
        } yield h
        
        req
      }
      newRequest(uri, post, params, hs.toMap)
    }
  }
}