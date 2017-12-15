package com.qunar.base.qunit.dsl;

import com.qunar.base.qunit.command.CallStepCommand;
import com.qunar.base.qunit.command.ParameterizedCommand;
import com.qunar.base.qunit.command.StepCommand;
import com.qunar.base.qunit.context.Context;
import com.qunar.base.qunit.model.KeyValueStore;
import com.qunar.base.qunit.model.ServiceDesc;
import com.qunar.base.qunit.reporter.QJSONReporter;
import com.qunar.base.qunit.response.Response;

import java.util.*;

import static com.qunar.base.qunit.util.CloneUtil.cloneKeyValueStore;

/**
 * User: zhaohuiyu
 * Date: 2/20/13
 * Time: 4:08 PM
 */
public class DSLCommand extends ParameterizedCommand {

    private final static Set<String> COMMADND_RUNRECORD = new HashSet<String>();

    private DSLCommandDesc desc;
    private List<StepCommand> commands;
    private Map<String, Object> commandParam;
    private StepCommand currentCommand;
    private long startTime;
    public static List<Map<String, Object>> reportList = new ArrayList<Map<String, Object>>();

    public void addReportList(Map<String, Object> map){
        reportList.add(map);
    }

    public void setCurrentCommand(StepCommand currentCommand){
        this.currentCommand = currentCommand;
    }

    public StepCommand getCurrentCommand(){
        return currentCommand;
    }

    public long getStartTime(){
        return startTime;
    }

    public DSLCommand(DSLCommandDesc desc, List<KeyValueStore> params, List<StepCommand> commands) {
        super(params);
        this.desc = desc;
        this.commands = commands;
    }

    @Override
    protected Response doExecuteInternal(Response preResult, List<KeyValueStore> processedParams, Context context) throws Throwable {
        Response response = preResult;
        Boolean run = COMMADND_RUNRECORD.contains(desc.id());
        if (desc.runOnce() && run) {
            return response;
        }
        COMMADND_RUNRECORD.add(desc.id());
        Context childContext = new Context(context);
        Map<String, Map<String, Object>> staticDataMap = DSLDataProcess.dataMap.get(desc.id());
        Map<String, Map<String, Object>> dataMap = desc.data();
        for (KeyValueStore processedParam : processedParams) {
            childContext.addContext(processedParam.getName(), processedParam.getValue());
            if ("data".equals(processedParam.getName())){
                if (dataMap != null){
                    setCommandParam(dataMap.get(processedParam.getValue()));
                }
                addContext(staticDataMap, processedParam, childContext);
            	addContext(dataMap, processedParam, childContext);
            }
        }
        for (StepCommand child : commands) {
            if (!(child instanceof DSLCommand)) {
                startTime = System.nanoTime();
                if (child instanceof CallStepCommand) {
                    CallStepCommand callStepCommand = (CallStepCommand) child;
                    ServiceDesc serviceDesc = QJSONReporter.serviceDescMap.get(callStepCommand.serviceId());
                    if (serviceDesc != null) {
                        serviceDesc.called();
                    }

                    setCurrentCommand(child);
                    response = child.doExecute(response, childContext);

                    long duration = System.nanoTime() - startTime;
                    if (serviceDesc != null) {
                        serviceDesc.callSuccess();
                        serviceDesc.addDuration(duration);
                    }
                } else {
                    setCurrentCommand(child);
                    response = child.doExecute(response, childContext);
                }

                Map<String, Object> reportMap = child.toReport();
                reportMap.put("duration", System.nanoTime() - startTime);
                addReportList(reportMap);
            } else {
                setCurrentCommand(child);
                response = child.doExecute(response, childContext);
            }
        }
        return response;
    }

    @Override
    protected StepCommand doClone() {
        List<StepCommand> cloneCommands = new ArrayList<StepCommand>();
        for (StepCommand command : commands) {
            cloneCommands.add(command.cloneCommand());
        }
        //update by lzy: 原来desc和params是使用引用复制,现在使用值复制
        return new DSLCommand(this.desc.clone(), cloneKeyValueStore(this.params), cloneCommands);
    }

    @Override
    public Map<String, Object> toReport() {
        /*Map<String, Object> details = new HashMap<String, Object>();
        details.put("stepName", "执行:");
        details.put("name", desc.desc());
        if (commandParam != null){
            details.put("params", convertMapToList(getCommandParam()));
        } else {
            details.put("params", params);
        }*/
        Map<String, Object> details = new HashMap<String, Object>();
        if (reportList != null){
            details.put("dslReport", reportList);
            details.put("currentCommand", getCurrentCommand());
        }
        return details;
    }

    private void addContext(Map<String, Map<String, Object>> dataMap, KeyValueStore processedParam, Context childContext){
        if (dataMap == null){
            return;
        }
    	Map<String, Object> keyValueMap = dataMap.get(processedParam.getValue());
    	Iterator iterator = keyValueMap.entrySet().iterator();
    	while(iterator.hasNext()){
    		Map.Entry<String, String> entry = (Map.Entry<String, String>)iterator.next();
    		childContext.addContext(entry.getKey(), entry.getValue());
    	}
    }

    public Map<String, Object> getCommandParam() {
        return commandParam;
    }

    public void setCommandParam(Map<String, Object> commandParam) {
        this.commandParam = commandParam;
    }

    public DSLCommandDesc getDesc(){
        return desc;
    }

    private List<KeyValueStore> convertMapToList(Map<String, Object> map){
        List<KeyValueStore> keyValueStores = new ArrayList<KeyValueStore>();
        Iterator iterator = map.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<String, String> entry = (Map.Entry<String, String>)iterator.next();
            keyValueStores.add(new KeyValueStore(entry.getKey(), entry.getValue()));
        }

        return keyValueStores;
    }
}
