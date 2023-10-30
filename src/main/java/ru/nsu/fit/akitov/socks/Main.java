package ru.nsu.fit.akitov.socks;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Error: port not specified");
            return;
        }
        try {
            int port = Integer.parseInt(args[0]);
            new SocksProxyServer(port).run();
        } catch (NumberFormatException e) {
            System.out.println("Error: invalid port");
        }
    }

}