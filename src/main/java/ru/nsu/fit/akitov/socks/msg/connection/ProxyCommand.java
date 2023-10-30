package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;

@Getter
@AllArgsConstructor
public enum ProxyCommand {

    TCP_CONNECT(SocksConfiguration.CMD_TCP_CONNECT);

    public static ProxyCommand of(byte representation) {
        for (ProxyCommand command : ProxyCommand.values()) {
            if (command.representation == representation) {
                return command;
            }
        }
        throw new IllegalArgumentException("command is not supported");
    }

    private final byte representation;

}
