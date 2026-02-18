package io.mosip.packet.extractor.validator.impl;

import io.mosip.packet.core.constant.DateFormat;
import io.mosip.packet.core.constant.FieldType;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.QueryFilter;
import io.mosip.packet.core.dto.dbimport.TableRequestDto;
import io.mosip.packet.core.util.DateUtils;
import io.mosip.packet.extractor.validator.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Component
public class FilterValidation implements Validator {

    @Override
    public Boolean validate(DBImportRequest dbImportRequest) throws Exception {
        List<TableRequestDto> tableList = dbImportRequest.getTableDetails();

        for (TableRequestDto tableRequestDto : tableList) {
            if (tableRequestDto.getFilters() != null) {
                List<QueryFilter> filterList = tableRequestDto.getFilters();

                for(QueryFilter filter : filterList) {
                    switch(filter.getFilterCondition().toString()) {
                        case "EQUAL":
                        case "LESS_THEN_AND_EQUAL":
                        case "LESS_THEN":
                        case "GREATER_THEN":
                        case "GREATER_THEN_AND_EQUAL":
                            validateFilter(true, true, true, false, false, filter, tableRequestDto.getExecutionOrderSequence());
                            break;
                        case "BETWEEN":
                            validateFilter(true, true, true, true, true, filter, tableRequestDto.getExecutionOrderSequence());
                            break;
                    }
                }
            }
        }

        return true;
    }

    private Boolean validateFilter (boolean filterField, boolean fieldType, boolean fromValue, boolean toValue, boolean rangeCheck, QueryFilter filter, int sequence) throws Exception {
        if (filterField && (filter.getFilterField() == null || filter.getFilterField().isEmpty())) {
            throw new Exception("Filter : Filter Field Should not be Empty");
        }

        if (fieldType && filter.getFieldType() == null) {
            throw new Exception("Filter : " + filter.getFilterField() +  " FieldType Type Should not be Empty");
        }

        if (fromValue) {
            if ((filter.getFromValue() == null || filter.getFromValue().isEmpty()))
                if(sequence == 1) {
                    if(filter.getInitialFromValue() == null || filter.getInitialFromValue().isEmpty())
                        throw new Exception("Filter : " + filter.getFilterField() +  " Either From Value / Initial Value Should not be Empty");
                } else
                    throw new Exception("Filter : " + filter.getFilterField() +  " From Value Should not be Empty");

            if (filter.getFieldType().equals(FieldType.NUMBER)){
                try {
                    if(sequence == 1) {
                        int i = filter.getFromValue() != null ? Integer.parseInt(filter.getFromValue()) : Integer.parseInt(filter.getInitialFromValue());
                    } else {
                        Integer.parseInt(filter.getFromValue());
                    }
                } catch (Exception e) {
                    if(sequence == 1)
                        throw new Exception("Filter : " + filter.getFilterField() +  " From Value / Initial Value should be numeric for Field Type : " + filter.getFieldType());
                    else
                        throw new Exception("Filter : " + filter.getFilterField() +  " From Value should be numeric for Field Type : " + filter.getFieldType());
                }
            } else if (filter.getFieldType().equals(FieldType.DATE)){
                Date toDate = DateUtils.findDateFormat(filter.getFromValue() != null ? filter.getFromValue() : filter.getInitialFromValue());

                if(toDate == null)
                    if(sequence == 1)
                        throw new Exception("Invalid Date Format Entered in Filter From Value / Initial Value for " + filter.getFilterField() );
                    else
                        throw new Exception("Invalid Date Format Entered in Filter From Value for " + filter.getFilterField() );
            }  else if (filter.getFieldType().equals(FieldType.TIMESTAMP)){
// TODO Need to Implement Timestamp Filter

            }
        }

        if (toValue) {
            if (filter.getToValue() == null || filter.getToValue().isEmpty())
                throw new Exception("Filter : " + filter.getFilterField() +  " To Value Should not be Empty");

            if (filter.getFieldType().equals(FieldType.NUMBER)){
                try {
                    Integer.parseInt(filter.getToValue());
                } catch (Exception e) {
                    throw new Exception("Filter : " + filter.getFilterField() +  " To Value should be numeric for Field Type : " + filter.getFieldType());
                }
            } else if (filter.getFieldType().equals(FieldType.DATE)){
                Date toDate = DateUtils.findDateFormat(filter.getToValue());

                if(toDate == null)
                   throw new Exception("Invalid Date Format Entered in Filter To Value for " + filter.getFilterField() );
            }  else if (filter.getFieldType().equals(FieldType.TIMESTAMP)){
// TODO Need to Implement Timestamp Filter

            }
        }

        if((filter.getFromValue() != null && filter.getInitialFromValue() != null)
                || (filter.getToValue() != null && filter.getFromValue() == null))
            throw new Exception("Invalid Configuration, Either one set From & To or InitialFrom & InitialTo should be configured for " + filter.getFilterField() );

        if(rangeCheck ) {
            if (filter.getFieldType().equals(FieldType.NUMBER)){
                if(((new BigInteger(filter.getFromValue())).compareTo(new BigInteger(filter.getToValue())) > 1))
                    throw new Exception("Filter : " + filter.getFilterField() +  " From Value should be less than To value for field Type : " + filter.getFieldType());
            } else if (filter.getFieldType().equals(FieldType.DATE)){
                Date fromDate = DateUtils.findDateFormat(filter.getFromValue());
                Date toDate = DateUtils.findDateFormat(filter.getToValue());

                if(fromDate.after(toDate))
                    throw new Exception("Filter : " + filter.getFilterField() +  " From Date should be less than To date for field Type : " + filter.getFieldType());

            } else if (filter.getFieldType().equals(FieldType.TIMESTAMP)){
// TODO Need to Implement Timestamp Filter
            }
        }
        return true;
    }
}
