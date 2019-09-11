package org.dllearner.utilities.neo4j;

/**
 * Contains the settings around the Neosemantic plugin for Neo4J.
 *
 */
public class NeoSemanticsPluginSettings {

    private static String DEFAULT_RESOURCE_LABEL = "Resource";
    private static String DEFAULT_CLASS_LABEL = "Class";
    private static String DEFAULT_OBJECT_PROPERTY_LABEL = "Relationship";
    private static String DEFAULT_DATA_PROPERTY_LABEL = "Property";
    private static String DEFAULT_SUBCLASSOF_REL = "SCO";
    private static String DEFAULT_SUBPROPERTYOF_REL = "SPO";
    private static String DEFAULT_DOMAIN_REL = "DOMAIN";
    private static String DEFAULT_RANGE_REL = "RANGE";

    public static final NeoSemanticsPluginSettings STANDARD = new NeoSemanticsPluginSettings.Builder().build();

    private String resourceLabel;
    private String classLabel;
    private String objectPropertyLabel;
    private String dataPropertyLabel;
    private String subClassOfRel;
    private String subPropertyOfRel;
    private String domainRel;
    private String rangeRel;

    private NeoSemanticsPluginSettings(String resourceLabel,
                                      String classLabel,
                                      String objectPropertyLabel,
                                      String dataPropertyLabel,
                                      String subClassOfRel, String subPropertyOfRel,
                                      String domainRel, String rangeRel) {
        this.resourceLabel = resourceLabel;
        this.classLabel = classLabel;
        this.objectPropertyLabel = objectPropertyLabel;
        this.dataPropertyLabel = dataPropertyLabel;
        this.subClassOfRel = subClassOfRel;
        this.subPropertyOfRel = subPropertyOfRel;
        this.domainRel = domainRel;
        this.rangeRel = rangeRel;
    }

    public String getResourceLabel() {
        return resourceLabel;
    }

    public String getClassLabel() {
        return classLabel;
    }

    public String getObjectPropertyLabel() {
        return objectPropertyLabel;
    }

    public String getDataPropertyLabel() {
        return dataPropertyLabel;
    }

    public String getSubClassOfRel() {
        return subClassOfRel;
    }

    public String getSubPropertyOfRel() {
        return subPropertyOfRel;
    }

    public String getDomainRel() {
        return domainRel;
    }

    public String getRangeRel() {
        return rangeRel;
    }



    public static class Builder {

        private String resourceLabel = NeoSemanticsPluginSettings.DEFAULT_RESOURCE_LABEL;
        private String classLabel = NeoSemanticsPluginSettings.DEFAULT_CLASS_LABEL;
        private String objectPropertyLabel = NeoSemanticsPluginSettings.DEFAULT_OBJECT_PROPERTY_LABEL;
        private String dataPropertyLabel = NeoSemanticsPluginSettings.DEFAULT_DATA_PROPERTY_LABEL;
        private String subClassOfRel = NeoSemanticsPluginSettings.DEFAULT_SUBCLASSOF_REL;
        private String subPropertyOfRel = NeoSemanticsPluginSettings.DEFAULT_SUBPROPERTYOF_REL;
        private String domainRel = NeoSemanticsPluginSettings.DEFAULT_DOMAIN_REL;
        private String rangeRel = NeoSemanticsPluginSettings.DEFAULT_RANGE_REL;

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setResourceLabel(String resourceLabel) {
            this.resourceLabel = resourceLabel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setClassLabel(String classLabel) {
            this.classLabel = classLabel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setObjectPropertyLabel(String objectPropertyLabel) {
            this.objectPropertyLabel = objectPropertyLabel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setDataPropertyLabel(String dataPropertyLabel) {
            this.dataPropertyLabel = dataPropertyLabel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setSubClassOfRel(String subClassOfRel) {
            this.subClassOfRel = subClassOfRel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setSubPropertyOfRel(String subPropertyOfRel) {
            this.subPropertyOfRel = subPropertyOfRel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setDomainRel(String domainRel) {
            this.domainRel = domainRel;
            return this;
        }

        public org.dllearner.utilities.neo4j.NeoSemanticsPluginSettings.Builder setRangeRel(String rangeRel) {
            this.rangeRel = rangeRel;
            return this;
        }

        NeoSemanticsPluginSettings build() {
            return new NeoSemanticsPluginSettings(resourceLabel, classLabel, objectPropertyLabel, dataPropertyLabel, subClassOfRel, subPropertyOfRel, domainRel, rangeRel);
        }
    }


}