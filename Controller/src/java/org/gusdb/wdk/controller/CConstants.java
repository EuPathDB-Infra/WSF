package org.gusdb.wdk.controller;

public class CConstants {
    private CConstants() {
	; // no-op
    }

    protected static final String WDK_MODELCONFIGXML_PARAM = "wdkModelConfigXml_param";
    protected static final String WDK_MODELXML_PARAM = "wdkModelXml_param";
    protected static final String WDK_MODELSCHEMA_PARAM = "wdkModelSchema_param";
    protected static final String WDK_MODELPROPS_PARAM = "wdkModelProps_param";
    protected static final String WDK_LOGFILE_PARAM = "wdkLogFile_param";
    protected static final String WDK_MODELPARSER_PARAM = "wdkModelParser_param";

    public static final String WDK_RESULTFACTORY_KEY = "wdkResultFactory";
    public static final String WDK_MODEL_KEY = "wdkModel";
    public static final String WDK_QUESTION_KEY = "wdkQuestion";
    public static final String WDK_SUMMARY_KEY = "wdkSummary";
    public static final String WDK_RECORD_KEY = "wdkRecord";
    
    public static final String SHOW_QUESTION_MAPKEY = "show_question";
    public static final String SHOW_SUMMARY_MAPKEY = "show_summary";
    public static final String SHOW_RECORD_MAPKEY = "show_record";

    public static final String QUESTIONSETFORM_KEY = "questionSetForm";
    public static final String QUESTIONFORM_KEY = "questionForm";

    protected static final String DEFAULT_WDKMODELCONFIGXML = "/WEB-INF/wdk-config/wdkModelConfig.xml";
    protected static final String DEFAULT_WDKMODELXML = "/WEB-INF/wdk-config/wdkModel.xml";
    protected static final String DEFAULT_WDKMODELSCHEMA = "/WEB-INF/wdk-config/wdkModel.rng";
    protected static final String DEFAULT_WDKMODELPROPS = "/WEB-INF/wdk-config/wdkModel.props";
    protected static final String DEFAULT_WDKMODELPARSER = "org.gusdb.wdk.model.implementation.ModelXmlParser";

}
