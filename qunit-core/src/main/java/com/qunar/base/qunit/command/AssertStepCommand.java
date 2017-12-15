/*
* $$Id$$
* Copyright (c) 2011 Qunar.com. All Rights Reserved.
*/
package com.qunar.base.qunit.command;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.qunar.base.qunit.context.Context;
import com.qunar.base.qunit.exception.ExecuteException;
import com.qunar.base.qunit.model.KeyValueStore;
import com.qunar.base.qunit.response.Response;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qunar.base.qunit.util.CloneUtil.cloneKeyValueStore;

/**
 * 描述：
 * Created by JarnTang at 12-6-4 下午6:10
 *
 * @author  JarnTang
 */
public class AssertStepCommand extends ParameterizedCommand {
    private final static String separator = System.getProperty("line.separator", "\r\n");

    private String error = StringUtils.EMPTY;

    private String desc;

    public AssertStepCommand(List<KeyValueStore> params, String desc) {
        super(params);
        this.desc = desc;
    }

    @Override
    protected Response doExecuteInternal(Response preResult, List<KeyValueStore> processedParams, Context context) throws Throwable {
        Map<String, String> expectation = convertKeyValueStoreToMap(processedParams);
        try {
            logger.info("assert command<{}> is starting... ", expectation);
            preResult.verify(expectation);
            return preResult;
        } catch (Exception e) {
            String message = "assert step invoke has error,expect=" + expectation + separator + "result=" + preResult;
            logger.error(message, e);
            error = JSON.toJSONString(preResult, SerializerFeature.WriteClassName);
            throw new ExecuteException(message, e);
        } catch (Throwable t) {
            //验证不通过的情况
            error = t.getMessage();
            throw t;
        }
    }

    @Override
    public StepCommand doClone() {
        return new AssertStepCommand(cloneKeyValueStore(this.params), desc);
    }

    @Override
    public Map<String, Object> toReport() {
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("stepName", String.format("验证:%s", desc));
        List<KeyValueStore> params = new ArrayList<KeyValueStore>();
        params.addAll(this.params);
        //if (StringUtils.isNotBlank(error)) {
        //params.add(new KeyValueStore("实际值", error));
        details.put("processResponse", error);
        //}
        details.put("params", params);
        return details;
    }

    private Map<String, String> convertKeyValueStoreToMap(List<KeyValueStore> params) {
        Map<String, String> result = new HashMap<String, String>();
        for (KeyValueStore kvs : params) {
            Object value = kvs.getValue();
            if (value instanceof Map) {
                result.putAll((Map) value);
            } else {
                result.put(kvs.getName(), (String) value);
            }
        }
        return result;
    }

}
