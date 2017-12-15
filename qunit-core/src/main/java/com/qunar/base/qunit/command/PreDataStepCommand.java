/*
* $$Id$$
* Copyright (c) 2011 Qunar.com. All Rights Reserved.
*/
package com.qunar.base.qunit.command;

import com.qunar.base.qunit.config.PreDataStepConfig;
import com.qunar.base.qunit.context.Context;
import com.qunar.base.qunit.database.DbUnitWrapper;
import com.qunar.base.qunit.dataassert.CommUtils;
import com.qunar.base.qunit.exception.ExecuteException;
import com.qunar.base.qunit.model.KeyValueStore;
import com.qunar.base.qunit.response.Response;
import com.qunar.base.qunit.util.CloneUtil;
import com.qunar.base.qunit.util.KeyValueUtil;
import com.qunar.base.qunit.util.PropertyUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 准备数据阶段执行器
 * <p/>
 * Created by JarnTang at 12-6-4 下午6:01
 *
 * @author  JarnTang
 */
public class PreDataStepCommand extends ParameterizedCommand {

    String file;
    String database;
    String replaceStr;
    String cached;

    public PreDataStepCommand(List<KeyValueStore> params) {
        super(params);
    }

    @Override
    protected Response doExecuteInternal(Response preResult, List<KeyValueStore> processedParams, Context context) throws Throwable {
        file = KeyValueUtil.getValueByKey(PreDataStepConfig.FILE, processedParams);
        database = KeyValueUtil.getValueByKey(PreDataStepConfig.DATABASE, processedParams);
        replaceStr = KeyValueUtil.getValueByKey(PreDataStepConfig.REPLACETABLENAME, processedParams);
        cached = KeyValueUtil.getValueByKey(PreDataStepConfig.CACHED, processedParams);


        String globalCached = PropertyUtils.getProperty("cached", "false");
        boolean actualCached = getActualCached(globalCached, cached);
        DbUnitWrapper dbUnitWrapper = null;
        try {
            if (StringUtils.isBlank(file)) {
                logger.info("prepare data command, file is blank");
                return preResult;
            }
            String[] files = file.split(",");
            logger.info("prepare data command<file={}> is starting...", file);
            dbUnitWrapper = new DbUnitWrapper(database);
            for (String file : files) {
                if (StringUtils.isNotBlank(file)) {
                    dbUnitWrapper.prepareData(file, replaceStr, actualCached);
                }
            }

            //更新: 在结束前,标记binlog起点
            if (CommUtils.globalNeedRegister()) CommUtils.registerStart(context);

            return preResult;
        } catch (Exception e) {
            String message = String.format("prepare data step command invoke error, database=<%s>,file=<%s>", database, file);
            logger.error(message, e);
            throw new ExecuteException(message, e);
        }
    }


    @Override
    public StepCommand doClone() {
        return new PreDataStepCommand(CloneUtil.cloneKeyValueStore(params));
    }

    @Override
    public Map<String, Object> toReport() {
        Map<String, Object> details = new HashMap<String, Object>();
        details.put("stepName", "准备数据:");
        /*
        if (StringUtils.isNotEmpty(this.file)) {
            StringBuilder sb = new StringBuilder();
            sb.append("数据库:").append(database).append(",");
            sb.append("数据文件：").append("<a href=\"\" onclick=\"return showData.call(this);\">").append(this.file).append("</a>");
            if (replaceStr != null && replaceStr.split("->").length == 2) {
                sb.append(", 表名替换：").append(replaceStr);
            }
            details.put("name", sb.toString());
        }
        */
        List<KeyValueStore> params = new ArrayList<KeyValueStore>();
        params.add(new KeyValueStore(PreDataStepConfig.FILE, file));
        params.add(new KeyValueStore(PreDataStepConfig.DATABASE, database));
        params.add(new KeyValueStore(PreDataStepConfig.REPLACETABLENAME, replaceStr));
        params.add(new KeyValueStore(PreDataStepConfig.CACHED, cached));
        details.put("params", params);
        return details;
    }

    private boolean getActualCached(String globalCached, String cached) {
        if (StringUtils.isNotBlank(cached)) {
            return Boolean.valueOf(cached);
        }
        if (Boolean.valueOf(globalCached)) {
            return true;
        }
        return false;
    }

}
