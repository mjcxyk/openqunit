/**
 *
 */
package com.qunar.base.qunit.reporter;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qunar.base.qunit.command.CallStepCommand;
import com.qunar.base.qunit.command.StepCommand;
import com.qunar.base.qunit.context.Context;
import com.qunar.base.qunit.dsl.DSLCommand;
import com.qunar.base.qunit.dsl.DSLCommandDesc;
import com.qunar.base.qunit.event.StepEventListener;
import com.qunar.base.qunit.model.*;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.*;

import static org.apache.commons.lang.StringUtils.isEmpty;

/**
 * @author ziqiang.deng
 * @purpose: JSON report for Qunit
 */
public class QJSONReporter implements Reporter {
    private final static Logger logger = LoggerFactory.getLogger(QJSONReporter.class);

    private JSONArray reports = new JSONArray();
    private Appendable out;
    private SvnInfo svninfo;

    private Map<Object, Object> suiteMap;

    private CaseStatistics caseStatistics = new CaseStatistics();

    public QJSONReporter(Appendable out) {
        this.out = out;
    }

    public void done() {
        reports.add(suiteMap);
    }

    public void close() {
        try {
            out.append(reportAsString());
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        } finally {
            close(this.out);
        }
    }

    private void close(Appendable output) {
        try {
            if (output instanceof Flushable) {
                ((Flushable) output).flush();
            }
            if (output instanceof Closeable) {
                ((Closeable) output).close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public Map<Object, Object> getSuiteMap() {
        return suiteMap;
    }

    synchronized public void addFailed(String id){
        caseStatistics.addFailed(id);
    }

    synchronized public void addSuccess(String id) {
        caseStatistics.addSuccess(id);
    }
    synchronized public void setDuration(long duration) {
        caseStatistics.setDuration(duration);
    }
    synchronized public void setCurSteps(List steps) {
        //添加用例执行详细步骤信息
        caseStatistics.setSteps(steps);
    }
    synchronized public void setRunFile(String runFile) {
        caseStatistics.setRunFile(runFile);
    }
    public CaseStatistics getCaseStatistics() {
        return caseStatistics;
    }

    public String reportAsString() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("svnUrl", svninfo.getUrl());
        jsonObject.put("reversion", svninfo.getReversion());
        jsonObject.put("features", reports);
        jsonObject.put("services", this.serviceDescMap.values());
        jsonObject.put("dsl", this.dslCommands);
        return jsonObject.toJSONString();
    }

    @Override
    public void report(TestSuite testSuite) {
        suiteMap = testSuite.asMap();
    }

    private List<Object> getSuiteElements() {
        if (suiteMap == null) {
            return new ArrayList<Object>();
        }
        List<Object> elements = (List<Object>) suiteMap.get("elements");
        if (elements == null) {
            elements = new ArrayList<Object>();
            suiteMap.put("elements", elements);
        }
        return elements;
    }

    private Map<Object, List<Object>> getFeatureElement() {
        return (Map) getSuiteElements().get(getSuiteElements().size() - 1);
    }

    private List<Object> getSteps() {
        List<Object> steps = getFeatureElement().get("steps");
        if (steps == null) {
            steps = new ArrayList<Object>();
            getFeatureElement().put("steps", steps);
        }
        return steps;
    }

    private JSONArray getRows(List<KeyValueStore> params) {
        JSONArray rowsArray = new JSONArray();
        if (params == null || params.size() == 0) return rowsArray;
        for (KeyValueStore p : params) {
            JSONObject row = new JSONObject();
            JSONArray cells = new JSONArray();
            cells.add(p.getName());

            String rVal;
            Object value = p.getValue();
            if (value != null && !isEmpty(value.toString())) {
                rVal = StringEscapeUtils.escapeXml(value.toString());
            } else {
                rVal = "null";
            }
            cells.add(rVal);
            row.put("cells", cells);
            rowsArray.add(row);
        }
        return rowsArray;
    }


    public ReporterEventListener createStepListener() {
        return new ReporterEventListener();
    }

    @Override
    public void addSvnInfo(SvnInfo svnInfo) {
        this.svninfo = svnInfo;
    }

    public static Map<String, ServiceDesc> serviceDescMap = new HashMap<String, ServiceDesc>();

    @Override
    public void addService(ServiceDesc serviceDesc) {
        this.serviceDescMap.put(serviceDesc.getId(), serviceDesc);
    }

    private List<Map<String, String>> dslCommands = new ArrayList<Map<String, String>>();

    @Override
    public void addDSLCommand(DSLCommandDesc dslCommandDesc) {
        Map<String, String> dslCommand = new HashMap<String, String>();
        dslCommand.put("id", dslCommandDesc.id());
        dslCommand.put("desc", dslCommandDesc.desc());
        dslCommands.add(dslCommand);
    }

    public class ReporterEventListener implements StepEventListener {

        private long startTime;

        public void caseStarted(TestCase testCase, Context context) {
            Object job = context.getContext("job");
            Object build = context.getContext("build");
            logger.info("---TestCase {}--{}开始运行---", testCase.getId(), testCase.getDesc());
            Map e = testCase.asMap();
            e.put("start", new Date());
            getSuiteElements().add(e);
        }

        private boolean isOnJenkins(Object job, Object build) {
            return job != null && build != null;
        }

        public void caseFinished(TestCase testCase, Context context) {
            logger.info("---TestCase {}--{}运行完毕---", testCase.getId(), testCase.getDesc());
        }

        public void stepStarted(StepCommand sc) {
            if (sc instanceof CallStepCommand) {
                CallStepCommand callStepCommand = (CallStepCommand) sc;
                ServiceDesc serviceDesc = serviceDescMap.get(callStepCommand.serviceId());
                if (serviceDesc != null) {
                    serviceDesc.called();
                }
            }
            startTime = System.nanoTime();
        }

        public void stepFailed(StepCommand sc, Throwable e) {
            Map<String, Object> details = sc.toReport();
            if (details.get("dslReport") == null) {
                long duration = System.nanoTime() - startTime;
                if (sc instanceof CallStepCommand) {
                    CallStepCommand callStepCommand = (CallStepCommand) sc;
                    ServiceDesc serviceDesc = serviceDescMap.get(callStepCommand.serviceId());
                    if (serviceDesc != null) {
                        serviceDesc.addDuration(duration);
                    }
                }

                Map<String, Object> result = new HashMap<String, Object>();
                Map<Object, Object> stepMap = new HashMap<Object, Object>();
                result.put("duration", duration);
                result.put("status", "failed");
                if (e != null) {
                    result.put("error_message", e.getMessage());
                }
                append(details, stepMap, duration);
                stepMap.put("result", result);
                getSteps().add(stepMap);
            } else {
                dslStepFailed(details, e);
            }
        }

        private void append(Map<String, Object> details, Map<Object, Object> stepMap, long duration) {
            if (details != null) {
                String stepName = (String) details.get("stepName");
                List<KeyValueStore> params = (List<KeyValueStore>) details.get("params");
                String name = details.get("name") == null ? "" : details.get("name").toString();

                stepMap.put("keyword", stepName + " (" + duration / (1000 * 1000) + "ms)");
                stepMap.put("name", name);
                if(details.get("processResponse") != null){
                    stepMap.put("response", details.get("processResponse"));
                }
                if (!getRows(params).isEmpty())
                    stepMap.put("rows", getRows(params));
            }
        }

        public void stepFinished(StepCommand sc) {
            Map<String, Object> details = sc.toReport();
            if (details.get("dslReport") == null) {
                long duration = System.nanoTime() - startTime;
                if (sc instanceof CallStepCommand) {
                    CallStepCommand callStepCommand = (CallStepCommand) sc;
                    ServiceDesc serviceDesc = serviceDescMap.get(callStepCommand.serviceId());
                    if (serviceDesc != null) {
                        serviceDesc.callSuccess();
                        serviceDesc.addDuration(duration);
                    }
                }

                Map<String, Object> result = new HashMap<String, Object>();
                Map<Object, Object> stepMap = new HashMap<Object, Object>();
                result.put("duration", duration);
                result.put("status", "passed");
                stepMap.put("result", result);
                append(details, stepMap, duration);
                getSteps().add(stepMap);
            } else {
                List<Map<String, Object>> dslReprotList = (List<Map<String, Object>>) details.get("dslReport");
                int count = dslReprotList.size();
                for (int i = 0; i < count; i++) {
                    Map<String, Object> current = dslReprotList.get(i);

                    Map<String, Object> result = new HashMap<String, Object>();
                    Map<Object, Object> stepMap = new HashMap<Object, Object>();
                    result.put("duration", current.get("duration"));
                    result.put("status", "passed");
                    stepMap.put("result", result);
                    append(dslReprotList.get(i), stepMap, Long.valueOf(current.get("duration").toString()));
                    getSteps().add(stepMap);
                }
                DSLCommand.reportList.clear();
            }
        }

        private void dslStepFailed(Map<String, Object> details, Throwable e){
            List<Map<String, Object>> dslReprotList = (List<Map<String, Object>>) details.get("dslReport");
            int count = dslReprotList.size();
            for (int i = 0; i < count; i++) {
                Map<String, Object> current = dslReprotList.get(i);
                Map<String, Object> result = new HashMap<String, Object>();
                Map<Object, Object> stepMap = new HashMap<Object, Object>();
                result.put("duration", current.get("duration"));
                result.put("status", "passed");
                stepMap.put("result", result);
                append(current, stepMap, Long.valueOf(current.get("duration").toString()));
                getSteps().add(stepMap);
            }

            StepCommand lastCommand = (StepCommand) details.get("currentCommand");
            while (lastCommand instanceof DSLCommand){
                lastCommand = ((DSLCommand) lastCommand).getCurrentCommand();
            }
            long lastDuration = System.nanoTime() - startTime;
            if (lastCommand instanceof CallStepCommand) {
                CallStepCommand callStepCommand = (CallStepCommand) lastCommand;
                ServiceDesc serviceDesc = serviceDescMap.get(callStepCommand.serviceId());
                if (serviceDesc != null) {
                    serviceDesc.addDuration(lastDuration);
                }
            }

            Map<String, Object> result = new HashMap<String, Object>();
            Map<Object, Object> stepMap = new HashMap<Object, Object>();
            result.put("duration", lastDuration);
            result.put("status", "failed");
            if (e != null) {
                result.put("error_message", e.getMessage());
            }
            stepMap.put("result", result);

            append(lastCommand.toReport(), stepMap, lastDuration);
            getSteps().add(stepMap);
            DSLCommand.reportList.clear();
        }
    }
}
