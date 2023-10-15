package ru.nsu.fit.akitov.socks;

public class Main {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Error: port not specified");
            return;
        }
        new SocksProxyServer(Integer.parseInt(args[0])).run();
    }

}