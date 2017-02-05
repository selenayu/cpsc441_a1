/**
 * WebProxy Class
 * This program is a simple caching web proxy that can send GET requests to servers, receive and store response
 * objects as cache, and forward server messages to client. The cache allows client to retrieve requested objects
 * without connecting to server.
 * 
 * @author      Selena Yu
 * @version     1.3, 04 Feb, 2017
 * @notes
 * - All testing done on Mozilla FireFox		
 * - Sometimes the proxy may perceive the protocol identifier as the request type, thus returning the "400 BAD REQUEST" 
 * message. In this case, restarting the program and then reentering the request address should fix the problem. 
 */

import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;

public class WebProxy {

     /**
     *  Constructor that initalizes the server listenig port
         *
     * @param port      Proxy server listening port
     */
	ServerSocket sSocket;

	public WebProxy(int port) {

	/* Intialize server listening port */
		try {
			sSocket = new ServerSocket(port);
		}

		catch (Exception e) {
			System.out.println("Exception creating server socket: " + e.getMessage());
		}
	}

     /**
     * The webproxy logic goes here 
     */
	public void start(){

		String input;
		String hostName = "";
		String pathName = "";
		StringBuilder request = new StringBuilder();
		String separator = "\r\n";
		String[] allTokens = null;
		int counter = 0;
		Socket cSocket = null;
		Socket outSocket = null;

		//while(true) {
			try {
				
				//initialize client socket
				cSocket = sSocket.accept();

				//set up bufferedreader to read input from browser
				BufferedReader br = new BufferedReader(new InputStreamReader(cSocket.getInputStream()));
				
				//while end of header is not reached
				while ((input = br.readLine()).length() > 0) {
					
					//parse first line of the header for request type and pathname
					if(counter == 0) {
						String requestType = input.substring(0, 3);
						
						//end connection if request type is not GET
						if (!(requestType.equals("GET"))) {
							System.out.println("400 BAD REQUEST");
							cSocket.close();
							break;
						}
						
						//parse out pathname and apppend to request message
						int a = input.indexOf("/", 11);
						int b = input.indexOf("HTTP/1.1");					
						String newInput = "GET " + input.substring(a);
						pathName = input.substring(a,b);
						
						request.append(newInput);
						request.append(separator);						
					}
					
					//parse second line of the header for hostname
					else if (counter == 1){

					allTokens = input.split(" ");
					hostName = allTokens[1];

					request.append(input);
					request.append(separator);
					
					//empty line
					request.append(separator);
					}
					
					//counter used to keep track of line number in the header
					counter++;
				}
				
				//create new socket for forwarding message and write to server using outputstream
				outSocket = new Socket(hostName, 80);
				
				//calls uncaching method to check if there exists local cached object
				//retrieve object if exists
				String fullPath = hostName + pathName;
				byte[] cached = uncaching(fullPath);
				
				//if local cached object does not exist
				//send request to server 
				if (cached == null) {
					
					//forward request to server
					OutputStream out = outSocket.getOutputStream();
					out.write(request.toString().getBytes());
					out.flush();
					System.out.println("Sent request to server");
					
					//parse path to object
					int temp = fullPath.lastIndexOf("/");
					String fileName = fullPath.substring(temp);
					String newPath = fullPath.substring(0, temp);
				
					//create new directories to cache object if directories do not exist 
					File hostDir = new File(newPath);
					if (!(hostDir.exists())) {
						hostDir.mkdirs();
					}
				
					//create new cache object and outputstream to write to file
					File object = new File(newPath, fileName);
					DataOutputStream output = new DataOutputStream(new FileOutputStream(object));
					
					//receive and read response header from server
					DataInputStream in = new DataInputStream(outSocket.getInputStream());
					String aline = in.readLine();
					
					while(aline.length() != 0) {
						
						//check request status
						if (aline.startsWith("HTTP")){
							
							int index = aline.indexOf(" ") + 1;
							String response = aline.substring(index, index + 6);
							
							//end connection if request was unsuccessful
							if(!(response.equals("200 OK"))){
								System.out.println("400 BAD REQUEST");
								cSocket.close();
								break;
							}
						}
						aline = in.readLine();
					}
					
					//read response body from server
					OutputStream outStream = cSocket.getOutputStream();
					byte[] buffer = new byte[1024];		
					int b;
					int i = 0;
					
					//read until end of body
					while ((b = in.read(buffer, 0, buffer.length)) != -1) {
						
						//cache response using caching method
				        caching(object, output, aline, buffer, i, b);
				        //write response
						outStream.write(buffer, 0, b);
						outStream.flush();
						
						i = i + b;
					}
					
			        System.out.println("Sent response back to client");
					System.out.println("Request completed. Ending connection... Goodbye!");
			        			
			        //close all sockets
			        outSocket.close();
			        sSocket.close();
			        cSocket.close();
					
				}
		        
				//if there exists cached object
				//retrieve file
				else {
					DataOutputStream output = new DataOutputStream(cSocket.getOutputStream());
					output.write(cached);
					System.out.println("Object retrieved from cache");
					System.out.println("Request completed. Ending connection... Goodbye!");
					
			        outSocket.close();
			        sSocket.close();
			        cSocket.close();
				}
				
			}

			catch (Exception e) {
				System.out.println("Exception in start " + e.getMessage());
			}
		}
	
	/**
	 * caching method that writes server response to local file to save as cache
	 * @param object The file we are writing to
	 * @param out Stream used to write to the file
	 * @param line Response header
	 * @param data Response body
	 * @param offset Offset for the file
	 * @param len Length of bytes to be written
	 */
	public void caching(File object, DataOutputStream out, String line, byte[] data, int offset, int len) {
		
		try {
			//if writing to an empty file
			//include response header
			if (offset == 0) {
				out.writeBytes(line);
				out.write(data, 0, len);
				out.flush();

			}
			
			else {
				out.write(data, 0, len);
				out.flush();
			}
		}
		
		catch (Exception e) {
			System.out.println("Exception in caching " + e.getMessage());
		}	
	}
	
	/**
	 * uncaching method for testing if file exists and retrieving content if file exists
	 * @param path Path to file
	 * @return response An array of bytes read from the file, null if file doesn't exist
	 */
	public byte[] uncaching (String path) {
		
		byte[] response = null;
		
		File temp = new File(path);
		
		//display message and return null if file doesn't exist
		if (!(temp.exists())) {
			System.out.println("No cached object found, fetching from server...");
			return response;
		}
		
		//display message and read file content, then return content as an array of bytes
		else {
			
			System.out.println("Cached copy found, fetching from local cache...");
			
			try{
				FileInputStream in = new FileInputStream(temp);
				response = new byte[(int)temp.length()];
				in.read(response);
				in.close();
				
			}
			catch (Exception e) {
				System.out.println("Exception in uncaching " + e.getMessage()+"4");
			}
			return response;
		}
		
	}
		
	//}



/**
 * A simple test driver
*/
	public static void main(String[] args) {

                String server = "localhost"; // webproxy and client runs in the same machine
                int server_port = 0;
		try {
                // check for command line arguments
                	if (args.length == 1) {
                        	server_port = Integer.parseInt(args[0]);
                	}
                	else {
                        	System.out.println("wrong number of arguments, try again.");
                        	System.out.println("usage: java WebProxy port");
                        	System.exit(0);
                	}


                	WebProxy proxy = new WebProxy(server_port);

                	System.out.printf("Proxy server started...\n");
                	proxy.start();
        	} catch (Exception e) {
			System.out.println("Exception in main: " + e.getMessage());
                        e.printStackTrace();
	
		}
		
	}
}
