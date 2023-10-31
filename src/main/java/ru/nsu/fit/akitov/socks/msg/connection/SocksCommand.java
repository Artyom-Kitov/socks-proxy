package ru.nsu.fit.akitov.socks.msg.connection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import ru.nsu.fit.akitov.socks.SocksConfiguration;
import ru.nsu.fit.akitov.socks.msg.exception.CommandNotSupportedException;

@Getter
@AllArgsConstructor
public enum SocksCommand {

    TCP_CONNECT(SocksConfiguration.CMD_TCP_CONNECT);

    public static SocksCommand of(byte representation) throws CommandNotSupportedException {
        for (SocksCommand command : SocksCommand.values()) {
            if (command.representation == representation) {
                return command;
            }
        }
        throw new CommandNotSupportedException("command is not supported");
    }

    private final byte representation;

}
