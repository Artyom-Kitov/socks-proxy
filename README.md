# SOCKS5 Proxy in Java

This is a simple SOCKS5 proxy implementation written in Java. It allows you to create a SOCKS5 proxy server with just one command line argument, which specifies the port number.

## Requirements

- Java 17 or higher

## Usage

1. Clone the repository:

```bash
git clone https://github.com/Artyom-Kitov/socks-proxy.git
cd socks-proxy
```

2. Compile the Java files:

```bash
./gradlew jar
```

3. Run the proxy server by providing the port number as a command line argument:

```bash
java -jar build/libs/socks-proxy-1.0-beta.jar <port>
```

Replace `<port>` with the desired port number on which you want the SOCKS5 proxy to listen.

4. The SOCKS5 proxy server is now running and ready to accept incoming connections on the specified port.

## Example

To run the proxy server on port 8080, execute the following command:

```bash
java -jar build/libs/socks-proxy-1.0-alpha.jar 8080
```

## Note

This implementation is a basic SOCKS5 proxy server and might not support all advanced features.
