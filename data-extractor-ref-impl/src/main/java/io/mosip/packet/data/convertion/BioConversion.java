package io.mosip.packet.data.convertion;

import io.mosip.biometrics.util.CommonUtil;
import io.mosip.biometrics.util.Conversion;
import io.mosip.biometrics.util.ConvertRequestDto;
import io.mosip.biometrics.util.ImageType;
import io.mosip.biometrics.util.face.FaceDecoder;
import io.mosip.biometrics.util.face.FaceEncoder;
import io.mosip.biometrics.util.finger.FingerDecoder;
import io.mosip.biometrics.util.finger.FingerEncoder;
import io.mosip.biometrics.util.finger.FingerImageCompressionType;
import io.mosip.biometrics.util.iris.IrisDecoder;
import io.mosip.biometrics.util.iris.IrisEncoder;
import io.mosip.commons.packet.constants.Biometric;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.BioSubType;
import io.mosip.packet.core.constant.DataFormat;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.spi.BioConvertorApiFactory;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class BioConversion implements BioConvertorApiFactory {

    private static final Logger LOGGER = DataProcessLogger.getLogger(BioConversion.class);

    @SneakyThrows
    @Override
    public byte[] convertImage(DataFormat currentFormat, List<DataFormat> destFormats, byte[] imageData, String fieldName) throws Exception {
        ImageType srcImageType = getImageType(currentFormat);
        List<ImageType> destImageTypes = destFormats.stream().map(imageType -> {
            try {
                return getImageType(imageType);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).collect(Collectors.toList());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] byteData = imageData;
        ConvertRequestDto requestDto = new ConvertRequestDto();
        for(ImageType toFormat : destImageTypes) {
            if(srcImageType.equals(toFormat)) {
                srcImageType = toFormat;
            } else {
                Conversion conversion = Conversion.valueOf(srcImageType.getName() + "_TO_" +toFormat.getName());

                switch (conversion) {
                    case JPEG2000_TO_JPEG2000:
                    case JPEG_TO_JPEG2000:
                    case PNG_TO_JPEG2000:
                    case WEBP_TO_JPEG2000:
                    case BMP_TO_JPEG2000:
                        byteData = CommonUtil.convertImageToJP2UsingOpenCV(byteData, 1000);
                        srcImageType = toFormat;
                        break;
                    case JPEG2000_TO_JPEG:
                    case JPEG_TO_JPEG:
                    case PNG_TO_JPEG:
                    case WEBP_TO_JPEG:
                    case BMP_TO_JPEG:
                        byteData = CommonUtil.convertImageToJPEGUsingOpenCV(byteData, 100);
                        srcImageType = toFormat;
                        break;
                    case JPEG2000_TO_BMP:
                    case JPEG_TO_BMP:
                    case PNG_TO_BMP:
                    case WEBP_TO_BMP:
                        byteData = CommonUtil.convertImageToBMPUsingOpenCV(byteData);
                        srcImageType = toFormat;
                        break;
                    case JPEG2000_TO_PNG:
                    case JPEG_TO_PNG:
                    case PNG_TO_PNG:
                    case WEBP_TO_PNG:
                    case BMP_TO_PNG:
                        byteData =  CommonUtil.convertImageToPNGUsingOpenCV(byteData, 0);
                        srcImageType = toFormat;
                        break;
                    case JPEG2000_TO_WEBP:
                    case JPEG_TO_WEBP:
                    case PNG_TO_WEBP:
                    case WEBP_TO_WEBP:
                    case BMP_TO_WEBP:
                        byteData =  CommonUtil.convertImageToWEBPUsingOpenCV(byteData, 101);
                        srcImageType = toFormat;
                        break;
                    case JPEG2000_TO_ISO:
                        byteData = imageConversion(ImageType.JPEG2000, 0, fieldName, byteData);
                        srcImageType = toFormat;
                        break;
                    case WSQ_TO_ISO:
                        byteData = imageConversion(ImageType.WSQ, 0, fieldName, byteData);
                        srcImageType = toFormat;
                        break;
                    case JPEG_TO_ISO:
                        byteData = imageConversion(ImageType.JPEG, 0, fieldName, byteData);
                        srcImageType = toFormat;
                        break;
                    case PNG_TO_ISO:
                        byteData = imageConversion(ImageType.PNG, 0, fieldName, byteData);
                        srcImageType = toFormat;
                        break;
                    case ISO_TO_JPEG:
                        byteData = imageConversion(ImageType.ISO, 1, fieldName, byteData);
                        srcImageType = toFormat;
                        break;
                    case ISO_TO_JPEG2000:
                        byteData = imageConversion(ImageType.ISO, 2, fieldName, byteData);
                        byteData = CommonUtil.convertImageToJP2UsingOpenCV(byteData, 1000);
                        srcImageType = toFormat;
                        break;
                    case ISO_TO_PNG:
                        byteData = imageConversion(ImageType.ISO, 2, fieldName, byteData);
                        byteData =  CommonUtil.convertImageToPNGUsingOpenCV(byteData, 0);
                        srcImageType = toFormat;
                        break;
                    case ISO_TO_BMP:
                        byteData = imageConversion(ImageType.ISO, 2, fieldName, byteData);
                        byteData =  CommonUtil.convertImageToBMPUsingOpenCV(byteData);
                        srcImageType = toFormat;
                        break;
                    case ISO_TO_WEBP:
                        byteData = imageConversion(ImageType.ISO, 2, fieldName, byteData);
                        byteData =  CommonUtil.convertImageToWEBPUsingOpenCV(byteData, 101);
                        srcImageType = toFormat;
                        break;
                    case WSQ_TO_JPEG:
                        byteData =  CommonUtil.convertBufferedImageToJPEGUsingOpenCV(CommonUtil.convertWSQToBufferedImage(byteData), 100);
                        srcImageType = toFormat;
                        break;
                    case WSQ_TO_BMP:
                        byteData =  CommonUtil.convertBufferedImageToBMPUsingOpenCV(CommonUtil.convertWSQToBufferedImage(byteData));
                        srcImageType = toFormat;
                        break;
                    case WSQ_TO_PNG:
                        byteData =  CommonUtil.convertBufferedImageToPNGUsingOpenCV(CommonUtil.convertWSQToBufferedImage(byteData), 0);
                        srcImageType = toFormat;
                        break;
                    case WSQ_TO_JPEG2000:
                        byteData =  CommonUtil.convertBufferedImageToJP2UsingOpenCV(CommonUtil.convertWSQToBufferedImage(byteData), 1000);
                        srcImageType = toFormat;
                        break;
                    case WSQ_TO_WEBP:
                        byteData =  CommonUtil.convertBufferedImageToWEAPUsingOpenCV(CommonUtil.convertWSQToBufferedImage(byteData), 101);
                        srcImageType = toFormat;
                        break;
                    case JPEG2000_TO_WSQ:
                        byteData =  CommonUtil.convertBufferedImageToWSQUsingOpenCV(CommonUtil.convertImageToPNGUsingOpenCV(byteData, 0));
                        srcImageType = toFormat;
                        break;
                    case JPEG_TO_WSQ:
                    case PNG_TO_WSQ:
                    case ISO_TO_WSQ:
                        byteData = imageConversion(ImageType.ISO, 2, fieldName, byteData);
                        byteData =  CommonUtil.convertBufferedImageToWSQUsingOpenCV(CommonUtil.convertImageToPNGUsingOpenCV(byteData, 0));
                        srcImageType = toFormat;
                        break;
                    default:
                        throw new Exception("Implementation not found for the Format " + conversion);
                }
            }
        }

        return byteData;
    }

    @Override
    public byte[] writeFile(String fileName, byte[] imageData, DataFormat toFormat) throws IOException {
        File imagePath = new File(System.getProperty("user.dir") + "/Images");
        if(!imagePath.exists())
            imagePath.mkdirs();

        File bioFile = new File(imagePath.getAbsolutePath() + "/" + fileName + "."+ toFormat.getFileFormat());
        OutputStream os = new FileOutputStream(bioFile);
        os.write(imageData);
        os.close();
        return imageData;
    }

    private ImageType getImageType(DataFormat dataFormat) throws Exception {
        switch (dataFormat) {
            case JP2:
                return ImageType.JPEG2000;
            case PNG:
                return ImageType.PNG;
            case WSQ:
                return ImageType.WSQ;
            case JPEG:
                return ImageType.JPEG;
            case ISO:
                return ImageType.ISO;
            case BMP:
                return ImageType.BMP;
            default:
                throw new Exception("Invalid ImageType for Biometric Convertion " + toString());
        }
    }

    public byte[] imageConversion(ImageType imageType, Integer convertTo, String fieldName, byte[] bioValue) throws Exception {
        String[] values = fieldName.split("_");
        String bioAttribute = values.length > 1 ? values[1] : values[0];
        BiometricType biometricType = Biometric.getSingleTypeByAttribute(bioAttribute);
        String biometricSubType = BioSubType.getBioSubType(bioAttribute).getBioSubType();

        // Image Type 0 - JP2000 & 1 - WSQ format
        LOGGER.info("imageConversion :: imageType :: " + imageType.toString());

        // ConvertTo 0 - IMAGE_TO_ISO & 1 - ISO_TO_IMAGE
        LOGGER.info("imageConversion :: convertTo :: " + (convertTo == 0 ? "IMAGE_TO_ISO" : "ISO_TO_IMAGE"));

        // Example#Iris Right eye
        LOGGER.info("imageConversion :: biometricSubType :: " + biometricSubType);

        String purpose = "REGISTRATION";
        LOGGER.info("imageConversion :: purpose :: " + purpose);

        LOGGER.info("imageConversion :: Biometric Type :: " + biometricType.value());

        if (biometricType.equals(BiometricType.FACE)) {
            return doFaceConversion(purpose, imageType, convertTo, bioValue);
        } else if (biometricType.equals(BiometricType.IRIS)) {
            if (biometricSubType != null) {
                return doIrisConversion(purpose, imageType, convertTo, biometricSubType, bioValue);
            } else {
                LOGGER.info("imageConversion :: biometricSubType :: " + biometricSubType + " is empty for Iris");
            }
        } else if (biometricType.equals(BiometricType.FINGER)) {
            if (biometricSubType != null) {
                return doFingerConversion(purpose, imageType, convertTo, biometricSubType, bioValue);
            } else {
                LOGGER.info("imageConversion :: biometricSubType :: " + biometricSubType + " is empty for Iris");
            }
        }
        return null;
    }

    public byte[] doFaceConversion(String purpose, ImageType inputImageType, Integer convertTo, byte[] imageData) {
        LOGGER.info("doFaceConversion :: Started :: inputImageType ::" + inputImageType + " :: convertTo :: " + convertTo);
        FileOutputStream tmpOutputStream = null;
        try {
            ConvertRequestDto requestDto = new ConvertRequestDto();
            requestDto.setModality("Face");
            requestDto.setPurpose(purpose);
            requestDto.setVersion("ISO19794_5_2011");

            if (convertTo == 0) // Convert JP2000 to Face ISO/IEC 19794-5: 2011
            {
                if (imageData != null) {
                    requestDto.setImageType(0);
                    requestDto.setInputBytes(imageData);

                    return FaceEncoder.convertFaceImageToISO(requestDto);
                } else {
                    LOGGER.error("doFaceConversion :: Could Not convert the Image To ISO ");
                }
            } else if (convertTo == 1) // Convert Face ISO/IEC 19794-5: 2011 to JPG
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);

                return FaceDecoder.convertFaceISOToImageBytes(requestDto);
            } else if (convertTo == 2) // Convert Face ISO/IEC 19794-5: 2011 to ImageByte without Conversion
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);

                return FaceDecoder.getFaceBDIR(requestDto).getRepresentation().getRepresentationData().getImageData().getImage();
            }
        } catch (Exception ex) {
            LOGGER.info("doFaceConversion :: Error ", ex);
        } finally {
            try {
                if (tmpOutputStream != null)
                    tmpOutputStream.close();
            } catch (Exception ex) {
            }
        }
        LOGGER.info("doFaceConversion :: Ended :: ");
        return null;
    }

    public byte[] doIrisConversion(String purpose, ImageType imageType, Integer convertTo,
                                   String biometricSubType, byte[] imageData) {
        LOGGER.info("doIrisConversion :: Started :: ImageType :: " + imageType + " :: convertTo ::"
                + convertTo + " :: biometricSubType :: " + biometricSubType);
        FileOutputStream tmpOutputStream = null;
        try {
            int imageInputType;

            if(purpose.equalsIgnoreCase("AUTH")) {
                imageInputType = io.mosip.biometrics.util.iris.ImageType.CROPPED_AND_MASKED;
            } else {
                imageInputType = io.mosip.biometrics.util.iris.ImageType.CROPPED;
            }

            ConvertRequestDto requestDto = new ConvertRequestDto();
            requestDto.setModality("Iris");
            requestDto.setPurpose(purpose);
            requestDto.setVersion("ISO19794_6_2011");

            if (convertTo == 0) // Convert JP2000 to IRIS ISO/IEC 19794-6: 2011
            {
                if (imageData != null) {
                    requestDto.setImageType(imageInputType);
                    requestDto.setBiometricSubType(biometricSubType);
                    requestDto.setInputBytes(imageData);

                    return IrisEncoder.convertIrisImageToISO(requestDto);
                } else {
                    LOGGER.error("doIrisConversion :: Could Not convert the Image To ISO ");
                }
            } else if (convertTo == 1) // Convert IRIS ISO/IEC 19794-6: 2011 to JPG
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);
                return IrisDecoder.convertIrisISOToImageBytes(requestDto);
            } else if (convertTo == 2) // Convert IRIS ISO/IEC 19794-5: 2011 to ImageByte without Conversion
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);

                return IrisDecoder.getIrisBDIR(requestDto).getRepresentation().getRepresentationData().getImageData().getImage();
            }
        } catch (Exception ex) {
            LOGGER.info("doIrisConversion :: Error ", ex);
        } finally {
            try {
                if (tmpOutputStream != null)
                    tmpOutputStream.close();
            } catch (Exception ex) {
            }
        }
        LOGGER.info("doIrisConversion :: Ended :: ");
        return null;
    }

    public byte[] doFingerConversion(String purpose, ImageType imageType, Integer convertTo, String biometricSubType, byte[] imageData) throws Exception {
        LOGGER.info("doFingerConversion :: Started :: ImageType :: " + imageType + " :: convertTo ::"
                + convertTo + " :: biometricSubType :: " + biometricSubType);
        FileOutputStream tmpOutputStream = null;

        try {
            int imageInputType;
            if(purpose.equalsIgnoreCase("AUTH")) {
                switch (imageType) {
                    case WSQ:
                        imageInputType = FingerImageCompressionType.WSQ;
                        break;
                    case JPEG2000:
                        imageInputType = FingerImageCompressionType.JPEG_2000_LOSSY;
                        break;
                    default:
                        throw new Exception("Input Image Type Configuration missing for Image " + imageType);
                }
            } else {
                switch (imageType) {
                    case PNG:
                        imageInputType = FingerImageCompressionType.PNG;
                        break;
                    case WSQ:
                        imageInputType = FingerImageCompressionType.WSQ;
                        break;
                    case JPEG:
                        imageInputType = FingerImageCompressionType.JPEG_LOSSY;
                        break;
                    case JPEG2000:
                        imageInputType = FingerImageCompressionType.JPEG_2000_LOSS_LESS;
                        break;
                    case ISO:
                        imageInputType = 0;
                        break;
                    case WEBP:
                        imageInputType = 0;
                        break;
                    default:
                        throw new Exception("Input Image Type Configuration missing for Image " + imageType);
                }
            }


            ConvertRequestDto requestDto = new ConvertRequestDto();
            requestDto.setModality("Finger");
            requestDto.setPurpose(purpose);
            requestDto.setVersion("ISO19794_4_2011");

            if (convertTo == 0) // Convert JP2000/WSQ to Finger ISO/IEC 19794-4: 2011
            {
                if (imageData != null) {
                    requestDto.setImageType(imageInputType);
                    requestDto.setBiometricSubType(biometricSubType);
                    requestDto.setInputBytes(imageData);

                    return FingerEncoder.convertFingerImageToISO(requestDto);
                } else {
                    LOGGER.error("doFingerConversion :: Could Not convert the Image To ISO ");
                }
            } else if (convertTo == 1) // Convert Finger ISO/IEC 19794-4: 2011 to JPG/WSQ
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);

                return FingerDecoder.convertFingerISOToImageBytes(requestDto);
            }  else if (convertTo == 2) // Convert Finger ISO/IEC 19794-5: 2011 to ImageByte without Conversion
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);

                return FingerDecoder.getFingerBDIR(requestDto).getRepresentation().getRepresentationBody().getImageData().getImage();
            } else if (convertTo == 3) // Convert Finger ISO/IEC 19794-4: 2011 to BufferedImage
            {
                requestDto.setInputBytes(imageData);
                requestDto.setOnlyImageInformation(1);

                return FingerDecoder.convertFingerISOToImageBytes(requestDto);
            }
        } catch (Exception ex) {
            LOGGER.info("doFingerConversion :: Error ", ex);
        } finally {
            try {
                if (tmpOutputStream != null)
                    tmpOutputStream.close();
            } catch (Exception ex) {
            }
        }
        LOGGER.info("doFingerConversion :: Ended :: ");
        return null;
    }


}
