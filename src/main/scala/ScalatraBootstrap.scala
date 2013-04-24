import twitterproxy._
import org.scalatra._
import javax.servlet.ServletContext
import javax.crypto.spec.IvParameterSpec

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {    
    configure(context)
    
    val servlet = new TwitterProxyServlet
    val registration = context.addServlet(servlet.getClass.getName, servlet)
    registration.addMapping("/*")
    registration.setAsyncSupported(true)
  }
  
  private[this] def configure(context: ServletContext) {
    import TwitterProxyServlet._    
    
    val (appKey, appSecret, callbackUrl) = 
      try {
        def using[I <: {def close(): Unit}, T](in: I)(block: I => T) = {
          try { block(in) } 
          finally { if(in != null) in.close() }
        }
        
        using(Thread.currentThread.getContextClassLoader.getResourceAsStream("application.properties")) { in =>
          val props = new java.util.Properties
          props.load(in)
          
          (
            props.getProperty("twitterproxy.app-key"), 
            props.getProperty("twitterproxy.app-secret"), 
            props.getProperty("twitterproxy.callback-url")
          )  
        }              
      } catch {
        case e: Exception => throw new Error("Not correctly configured. Check application.properties", e)
      }
    
    context += TwitterProxyContextAttributeName -> twitterProxyContext(appKey, appSecret, callbackUrl)
    context += TwitterProxyCryptoAttributeName -> crypto
  }
  
  def crypto: Crypto = {
    // this is a no-op crypto and you are STRONGLY recommended to use a strong encryption / decryption algorithm
    // without encryption tokens and secrets will be transmitted in plaintext in cookies
    new Crypto {
      def encrypt(input: String) = input
      def decrypt(input: String) = input
    }
  }
  
  def twitterProxyContext(appKey: String, appSecret: String, callbackUrl: String): TwitterProxyContext = {
    val cfg = new TwitterProxyConfig {
      val AppKey = appKey
      val AppSecret = appSecret
      val CallbackUrl = callbackUrl
    }
    new TwitterProxyContext(cfg)
  }
}
