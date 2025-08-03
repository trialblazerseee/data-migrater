package io.mosip.packet.extractor.util;

import io.mosip.packet.core.constant.ValidatorEnum;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.extractor.validator.Validator;
import io.mosip.packet.extractor.validator.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
@Import({IdSchemaFieldValidator.class, FilterValidation.class})
public class ValidationUtil {

    @Autowired
    private IdSchemaFieldValidator idSchemaFieldValidator;

    @Autowired
    private FilterValidation filterValidation;

    @Autowired
    private OrderByValidator orderByValidator;

    @Autowired
    private BiometricFormatValidator biometricFormatValidator;

    @Autowired
    private IdentityObjectValidator identityObjectValidator;

    @Autowired
    private RequestHashValidator requestHashValidator;

    private HashMap<ValidatorEnum, Validator> validatorList = null;

    public HashMap<ValidatorEnum, Validator> getValidatorMap() {
        if(validatorList == null) {
            validatorList= new HashMap<>();
            validatorList.put(ValidatorEnum.ID_SCHEMA_VALIDATOR, idSchemaFieldValidator);
            validatorList.put(ValidatorEnum.FILTER_VALIDATOR, filterValidation);
            validatorList.put(ValidatorEnum.ORDERBY_VALIDATOR, orderByValidator);
            validatorList.put(ValidatorEnum.BIOMETRIC_FORMAT_VALIDATOR, biometricFormatValidator);
            validatorList.put(ValidatorEnum.IDENTITY_JSON_VALIDATOR, identityObjectValidator);
            validatorList.put(ValidatorEnum.REQUEST_HASH_VALIDATOR, requestHashValidator);
        }
        return validatorList;
    }


    public void validateRequest(DBImportRequest dbImportRequest, List<ValidatorEnum> validationList) throws Exception {
        boolean isValid = true;
        List<String> errorValidation = new ArrayList<>();
        for (ValidatorEnum validatorEnum : validationList) {
            boolean valid = true;
            try {
                valid = getValidatorMap().get(validatorEnum).validate(dbImportRequest);
            } catch (Exception e) {
                valid = false;
                errorValidation.add(validatorEnum.name() + " : " + e.getMessage());
            }

            if(isValid)
                isValid = valid;
        }

        if(!isValid) {
            throw new Exception("Error while Validating Request" + String.join("\n", errorValidation));
        }
    }
}
