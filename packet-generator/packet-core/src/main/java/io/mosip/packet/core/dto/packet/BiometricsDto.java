package io.mosip.packet.core.dto.packet;

import io.mosip.commons.packet.constants.Biometric;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiometricsDto {
	
	private byte[] attributeISO;
	private String bioAttribute;
	private String bioSubType;
	private String modalityName;
	private double qualityScore;
	private boolean isForceCaptured;
	private boolean isNotAvailable;
	private int numOfRetries;	
	private boolean isCaptured;
	private String subType;
	private double sdkScore;
	private String payLoad;
	private String signature;
	private String specVersion;
	private String remarks;
	

		
	public BiometricsDto(String bioAttribute, byte[] attributeISO, double qualityScore) {
		this.bioAttribute = bioAttribute;
		this.attributeISO = attributeISO;
		this.qualityScore = qualityScore;
		this.modalityName = Biometric.getModalityNameByAttribute(bioAttribute);
	}
}
