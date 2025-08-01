package io.mosip.packet.core.service.thread;

import java.io.IOException;
import java.sql.SQLException;

public interface ResultSetter {
    public void setResult(Object obj) throws SQLException, IOException, InterruptedException;
}
