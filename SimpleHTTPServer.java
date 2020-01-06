// I Like to explicitly import the libraries I am
// going to use rather than saying java.io.*. Personal
// preference and it tells me what I am using explicitly.

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.HashMap;


class SimpleHTTPServer
{
  // HTTP use "\r\n" as an end of line indicator
  private static String EOL = "\r\n";

  // This will hold the directory on the filesystem the server is running on
  // where the website will is located
  private String root;

  // The TCP port the server will listen on
  private int port;

  // HTTP indicates the file type ( jpeg, html, etc. ) by a header
  // called Content-Type.  This is a lookup map to get the content-type
  // from the file extension in the URL
  public static Map<String,String> typeMap;
  static
  {
    typeMap = new HashMap<String,String>();
    typeMap.put( "htm", "text/html" );
    typeMap.put( "html", "text/html" );
    typeMap.put( "txt", "text/plain" );
    typeMap.put( "js", "application/javascript" );
    typeMap.put( "json", "application/json" );
    typeMap.put( "css", "text/css" );
    typeMap.put( "xml", "text/xml" );
    typeMap.put( "hml", "text/html" );
    typeMap.put( "jpg", "image/jpeg" );
    typeMap.put( "jpeg", "image/jpeg" );
    typeMap.put( "gif", "image/gif" );
  }


  // Constructor.  Takes the port to listen on and the filesystem root for
  // the server
  public SimpleHTTPServer( int port, String root )
  {
    this.port = port;

    // Remove the / from the root if there is one.  This should be
    // in the path from the request line
    if ( root.charAt( root.length() - 1 ) == '/' )
    {
      root = root.substring( 0, root.length() - 1 );
    }

    this.root = root;
  }

  // The method where it all happens
  public void run()
  {
    try
    {
      // Create the server socket listening on the indicated port.  FWIW
      // HTTP default port is 80.  This is NOT a 'secure' server which would
      // speak HTTP over TLS (https).  I will create an example for that
      // in the future.  At this point it will add too much extra stuff and
      // just confuse things.
      ServerSocket server = new ServerSocket( port );

      // Loop forever accepting client connectinos
      while( true )
      {
        // When a client (eg. web browser) conects to the TCP port, server.accept()
        // returns that socket object.
        Socket client = server.accept();

        double time = System.currentTimeMillis() / 1000.0;

        // Using a PrintStream.  This gives us acccess to convenience functions like
        // println (headers) as well as write for bytes (body)
        // Get the output stream from the socket.  We write to the client by
        // writing to the stream like we would write to System.out
        PrintStream out   = new PrintStream( client.getOutputStream() );

        // Get the input stream.  We read from the client (the HTTP request in this case)
        // from that stream like you woudl read from System.in
        BufferedReader in = new BufferedReader( new InputStreamReader( client.getInputStream() ), 4096 );

        // Read the first line.  This should be the request line which looks like:
        // GET /index.html HTTP/1.1
        String requestLine = in.readLine();

        if ( requestLine == null )
        {
          // Perhaps there is a dead client socket on the accept queue.  favicon maybe?
          // Or a timeout?
          // It seems to happen after a successful request and a wait time and then right
          // before the next request
          // It looks like it happens when a FIN is received and no data was ever sent
          // Confirmed.  accept does not return until some data is sent or a FIN is received.
          // This is probably true for a RST as well.
          // I am just going to ignore this case for the SimpleHTTPServer
          // System.out.printf( "%f %s %d\n", time, "requestLine was null for some reason: remote port:", client.getPort() );
          continue;
        }

        // split the request line on spaces
        String[] parts = requestLine.split( " ", 3 );

        String method   = parts[0]; // HTTP method ( GET, POST, PUT, etc )
        String path     = parts[1]; // THe path portion of the URL.  Says what file to fetch
        String protocol = parts[2]; // The HTTP protocol used. Unused in this program

        // Log the request to System.out
        System.out.printf( "%f %s %s %s %s %d\n", time, method, path, protocol, client.getInetAddress(), client.getPort() );

        if ( ! method.equals( "GET" ) )
        {
          // We have only implemented GET.  Return an error to any other
          // request method
          respond( 405, "Method Not Allowed", "", "", out );
        }
        else
        {
          // If the path does not start with a '/' add it for simplicity
          if ( ! (path.charAt( 0 ) == '/') )
          {
            path = "/" + path;
          }

          // Get the filename for the file on disk.  This is the server root plus the path
          // indicarted in the request line.
          String filename = root + path;

          // Create a file object
          File file = new File( filename );

          if ( ! file.isFile() )
          {
            // If the file does not exist return a 404 error.
            respond( 404, "File Not Found", "", "", out );
          }
          else
          {
            // if it does, return the file
            long len = file.length();
            FileInputStream fin = new FileInputStream( file );
            String contentType = contentTypeFromPath( path );

            respond( 200, "OK", fin, len, contentType, out );
          }

          
        }

        // close the socket streams thus closing the socket itself
        out.close();
        in.close();
      }
      

    }
    catch( IOException e )
    {
      System.err.println( "Error while processing request: " + e );
    }
    
  }

  // Lookup the content type based on the file extensions
  private static String contentTypeFromPath( String path )
  {
    String defaultContentLength = "text/plain";

    String[] parts = path.split( "/" );
    String file = parts[parts.length-1];
    parts = file.split( "\\." );

    // If there is less than two parts after the split it means there is no
    // extension
    if ( parts.length < 2 )
    {
      return defaultContentLength;
    }
    
    String extension = parts[parts.length-1].toLowerCase();


    String contentType = typeMap.get( extension );
    if ( contentType == null )
    {
      contentType = defaultContentLength;
    }

    return contentType;
    
  }

  // Send a file response from the InputStream
  private void respond( int code, String message, InputStream in, long size, String contenttype, PrintStream out )
  {
    // Send the status line
    // eg: HTTP/1.1 200 OK
    String line = "HTTP/1.1 " + code + " " + message + EOL;
    out.print( line );

    // Send the needed HTTP headers
    out.print( "Content-type: " + contenttype + EOL );
    out.print( "Content-length: " + size + EOL );

    // The empty line indicated the headers are complete.
    out.print( EOL );

    int r;
    byte[] buffer = new byte[1024];

    try
    {
      // read from the inputstream (file in this case) and
      // write the bytes to the socket's output stream
      while( (r = in.read( buffer, 0, buffer.length )) != -1 )
      {
        out.write( buffer, 0, r );
      }
      out.flush();
    }
    catch( IOException e )
    {
      // TODO: Handle this exception
      System.err.println( "Exception: " + e );
    }
  }
  

  // Send a simple statuc response (for error responses)
  private void respond( int code, String message, String payload, String contenttype, PrintStream out )
  {
    String line = "HTTP/1.1 " + code + " " + message + EOL;

    out.write( line.getBytes(), 0, line.length() );
      
    if ( payload.length() > 0 )
    {
      out.print( "Content-type: " + contenttype + EOL );
      out.print( "Content-length: " + payload.length() + EOL );
      out.print( EOL );
      out.write( payload.getBytes(), 0, payload.length() );
      out.flush();
    }
    else
    {
      out.write( EOL.getBytes(), 0, EOL.length() );
    }
      
  }

  public static void main(String argv[]) 
  {
    int port = 80;
    String root = "./";

    // This is all command line argumetn handling
    if ( argv.length > 0 )
    {
      if ( argv[0].equals( "--help" ) )
      {
        System.out.println( "Usage: java HTTPServer <port> <root>" );
        System.exit(0);
      }
      
      try
      {
        port = Integer.parseInt( argv[0] );
      }
      catch( NumberFormatException e )
      {
        System.err.println( "Invalid port: " + argv[0] );
        System.exit(1);
      }

      if ( port < 0 || port > (Math.pow( 2, 16 ) - 1) )
      {
        System.err.println( "Port number out of range: " + port );
        System.exit(1);
      }

      if ( argv.length > 1 )
      {
        root = argv[1];
      }
    }

    // Insantiate the server and run it
    SimpleHTTPServer server = new SimpleHTTPServer( port, root );
    server.run();
    
  } 
  
} 



