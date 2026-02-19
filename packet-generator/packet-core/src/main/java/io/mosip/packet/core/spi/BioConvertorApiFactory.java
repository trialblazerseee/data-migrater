package io.mosip.packet.core.spi;

import io.mosip.packet.core.constant.DataFormat;
import io.mosip.packet.core.dto.dbimport.FieldFormatRequest;

import java.io.IOException;
import java.util.List;

public interface BioConvertorApiFactory {
    public byte[] convertImage(DataFormat srcFormat, List<DataFormat> destFormat, byte[] imageData, String fieldName) throws Exception;
    public byte[] writeFile(String fileName, byte[] imageData, DataFormat toFormat) throws IOException;
}
