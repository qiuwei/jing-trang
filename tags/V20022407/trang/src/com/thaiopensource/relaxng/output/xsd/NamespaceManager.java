package com.thaiopensource.relaxng.output.xsd;

import com.thaiopensource.relaxng.output.xsd.basic.Element;
import com.thaiopensource.relaxng.output.xsd.basic.Attribute;
import com.thaiopensource.relaxng.output.xsd.basic.Schema;
import com.thaiopensource.relaxng.output.xsd.basic.Particle;
import com.thaiopensource.relaxng.output.xsd.basic.SchemaWalker;
import com.thaiopensource.relaxng.output.xsd.basic.GroupDefinition;
import com.thaiopensource.relaxng.output.xsd.basic.AttributeGroupDefinition;
import com.thaiopensource.relaxng.output.xsd.basic.Structure;
import com.thaiopensource.relaxng.output.xsd.basic.Include;
import com.thaiopensource.relaxng.output.xsd.basic.Definition;
import com.thaiopensource.relaxng.output.xsd.basic.Wildcard;
import com.thaiopensource.relaxng.output.xsd.basic.WildcardElement;
import com.thaiopensource.relaxng.output.xsd.basic.WildcardAttribute;
import com.thaiopensource.relaxng.output.common.Name;
import com.thaiopensource.relaxng.output.OutputDirectory;

import java.util.List;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

public class NamespaceManager {
  private final Schema schema;
  private final Map elementNameMap = new HashMap();
  private final Map attributeNameMap = new HashMap();

  private static final String ANON = "anon";

  static class SourceUri {
    String targetNamespace;
    // list of strings giving included URIs
    List includes = new Vector();
  }

  static class TargetNamespace {
    String rootSchema;
    List movedStructures = new Vector();
    Set movedStructureSet = new HashSet();
    Map movedStructureNameMap = new HashMap();
    Set movedElementNameSet = new HashSet();
    Set movedAttributeNameSet = new HashSet();
    boolean movedOtherElement = false;
    boolean movedOtherAttribute = false;
    String otherElementName;
    String otherAttributeName;
  }

  static class NameInfo {
    static final int OCCUR_NONE = 0;
    static final int OCCUR_NESTED = 1;
    static final int OCCUR_TOP = 2;
    static final int OCCUR_MOVE = 3;
    static final int OCCUR_ROOT = 4;
    int occur;
    Structure globalType;
  }

  // Maps sourceUri to SourceUri
  private final Map sourceUriMap = new HashMap();
  // Maps targetNamespace to TargetNamespace
  private final Map targetNamespaceMap = new HashMap();

  class IncludeFinder extends SchemaWalker {
    private final SourceUri source;
    IncludeFinder(Schema schema) {
      source = lookupSourceUri(schema.getUri());
      schema.accept(this);
    }

    public void visitInclude(Include include) {
      Schema included = include.getIncludedSchema();
      source.includes.add(included.getUri());
      new IncludeFinder(included);
    }
  }

  class RootMarker extends SchemaWalker {
    public void visitGroup(GroupDefinition def) {
    }

    public Object visitElement(Element p) {
      NameInfo info = lookupElementName(p.getName());
      info.globalType = p;
      info.occur = NameInfo.OCCUR_ROOT;
      lookupTargetNamespace(p.getName().getNamespaceUri());
      return null;
    }
  }

  static class NamespaceUsage {
    int elementCount;
    int attributeCount;
    static boolean isBetter(NamespaceUsage n1, NamespaceUsage n2) {
      return (n1.elementCount > n2.elementCount
              || (n1.elementCount == n2.elementCount
                  && n1.attributeCount > n2.attributeCount));
    }
  }

  class TargetNamespaceSelector extends SchemaWalker {
    private boolean nested;
    private final Map namespaceUsageMap = new HashMap();

    TargetNamespaceSelector(Schema schema) {
      schema.accept(this);
      lookupSourceUri(schema.getUri()).targetNamespace = selectTargetNamespace(schema);
    }

    public Object visitElement(Element element) {
      NamespaceUsage usage = getUsage(element.getName().getNamespaceUri());
      if (!nested)
        usage.elementCount++;
      boolean saveNested = nested;
      nested = true;
      element.getComplexType().accept(this);
      nested = saveNested;
      return null;
    }

    public Object visitAttribute(Attribute a) {
      NamespaceUsage usage = getUsage(a.getName().getNamespaceUri());
      if (!nested)
        usage.attributeCount++;
      return null;
    }


    public Object visitWildcardElement(WildcardElement p) {
      return visitWildcard(p.getWildcard());
    }

    public Object visitWildcardAttribute(WildcardAttribute a) {
       return visitWildcard(a.getWildcard());
    }

    private Object visitWildcard(Wildcard wc) {
      String ns = otherNamespace(wc);
      if (ns != null) {
        lookupTargetNamespace(ns);
        if (!nested)
          getUsage(ns).attributeCount++;
      }
      return null;
    }

    private NamespaceUsage getUsage(String ns) {
      NamespaceUsage usage = (NamespaceUsage)namespaceUsageMap.get(ns);
      if (usage == null) {
        usage = new NamespaceUsage();
        namespaceUsageMap.put(ns, usage);
        if (!ns.equals(""))
          lookupTargetNamespace(ns);
      }
      return usage;
    }

    public void visitInclude(Include include) {
      new TargetNamespaceSelector(include.getIncludedSchema());
    }

    String selectTargetNamespace(Schema schema) {
      Map.Entry best = null;
      for (Iterator iter = namespaceUsageMap.entrySet().iterator(); iter.hasNext();) {
        Map.Entry tem = (Map.Entry)iter.next();
        if (best == null
                || NamespaceUsage.isBetter((NamespaceUsage)tem.getValue(),
                                           (NamespaceUsage)best.getValue()))
          best = tem;
      }
      namespaceUsageMap.clear();
      if (best == null)
        return null;
      String targetNamespace = (String)best.getKey();
      // for "" case
      lookupTargetNamespace(targetNamespace);
      return targetNamespace;
    }
  }

  class GlobalElementSelector extends SchemaWalker {
    private final boolean absentTargetNamespace;
    private boolean nested = false;

    GlobalElementSelector(Schema schema) {
      absentTargetNamespace = getTargetNamespace(schema.getUri()).equals("");
      schema.accept(this);
    }

    public Object visitElement(Element element) {
      Name name = element.getName();
      String ns = name.getNamespaceUri();
      if (!name.getNamespaceUri().equals("") || absentTargetNamespace) {
        NameInfo info = lookupElementName(name);
        int occur = nested ? NameInfo.OCCUR_NESTED : NameInfo.OCCUR_TOP;
        if (occur > info.occur) {
          info.occur = occur;
          info.globalType = element;
        }
        else if (occur == info.occur && !element.equals(info.globalType))
          info.globalType = null;
      }
      boolean saveNested = nested;
      nested = true;
      element.getComplexType().accept(this);
      nested = saveNested;
      return null;
    }

    public void visitInclude(Include include) {
      new GlobalElementSelector(include.getIncludedSchema());
    }
  }

  class StructureMover extends SchemaWalker {
    private String currentNamespace;

    StructureMover(Schema schema) {
      this(getTargetNamespace(schema.getUri()));
      schema.accept(this);
    }

    StructureMover(String currentNamespace) {
      this.currentNamespace = currentNamespace;
    }

    public Object visitElement(Element p) {
      NameInfo info = lookupElementName(p.getName());
      String ns = p.getName().getNamespaceUri();
      if (ns.equals(currentNamespace) || (ns.equals("") && !p.equals(info.globalType)))
        p.getComplexType().accept(this);
      else {
        noteMoved(info, p);
        moveStructure(p);
        p.getComplexType().accept(new StructureMover(ns));
      }
      return null;
    }

    public Object visitAttribute(Attribute a) {
      String ns = a.getName().getNamespaceUri();
      if (!ns.equals("") && !ns.equals(currentNamespace)) {
        noteMoved(lookupAttributeName(a.getName()), a);
        moveStructure(a);
      }
      return null;
    }

    private void noteMoved(NameInfo info, Structure s) {
      if (info.occur < NameInfo.OCCUR_MOVE) {
        info.occur = NameInfo.OCCUR_MOVE;
        info.globalType = s;
      }
      else if (info.occur == NameInfo.OCCUR_MOVE && !s.equals(info.globalType))
        info.globalType = null;
    }

    private void moveStructure(Structure p) {
      TargetNamespace tn = lookupTargetNamespace(p.getName().getNamespaceUri());
      if (!tn.movedStructureSet.contains(p)) {
        tn.movedStructureSet.add(p);
        tn.movedStructures.add(p);
      }
    }

    public void visitInclude(Include include) {
      new StructureMover(include.getIncludedSchema());
    }

    public Object visitWildcardElement(WildcardElement p) {
      return visitWildcard(p.getWildcard(), true);
    }

    public Object visitWildcardAttribute(WildcardAttribute a) {
      return visitWildcard(a.getWildcard(), false);
    }

    private Object visitWildcard(Wildcard wc, boolean isElement) {
      String ns = otherNamespace(wc);
      if (ns != null && !ns.equals(currentNamespace)) {
        TargetNamespace tn = lookupTargetNamespace(ns);
        if (isElement)
          tn.movedOtherElement = true;
        else
          tn.movedOtherAttribute = true;
      }
      return null;
    }
  }

  NamespaceManager(Schema schema, SourceUriGenerator sug) {
    this.schema = schema;
    new IncludeFinder(schema);
    schema.accept(new RootMarker());
    assignTargetNamespaces();
    new GlobalElementSelector(schema);
    chooseRootSchemas(sug);
    new StructureMover(schema);
  }

  private void assignTargetNamespaces() {
    new TargetNamespaceSelector(schema);
    // TODO maybe use info from <start> to select which targetNamespace of included schemas to use
    String ns = filterUpTargetNamespace(schema.getUri());
    if (ns == null) {
      lookupTargetNamespace("");
      lookupSourceUri(schema.getUri()).targetNamespace = "";
      ns = "";
    }
    inheritDownTargetNamespace(schema.getUri(), ns);
  }

  private String filterUpTargetNamespace(String sourceUri) {
    String ns = getTargetNamespace(sourceUri);
    if (ns != null)
      return ns;
    List includes = lookupSourceUri(sourceUri).includes;
    if (includes.size() == 0)
      return null;
    Map occurMap = new HashMap();
    for (Iterator iter = includes.iterator(); iter.hasNext();) {
      String tem = filterUpTargetNamespace((String)iter.next());
      if (tem != null) {
        Integer count = (Integer)occurMap.get(tem);
        occurMap.put(tem, new Integer(count == null ? 1 : count.intValue() + 1));
      }
    }
    Map.Entry best = null;
    boolean bestAmbig = false;
    for (Iterator iter = occurMap.entrySet().iterator(); iter.hasNext();) {
      Map.Entry tem = (Map.Entry)iter.next();
      if (best == null || ((Integer)tem.getValue()).intValue() > ((Integer)best.getValue()).intValue()) {
        best = tem;
        bestAmbig = false;
      }
      else if (((Integer)tem.getValue()).intValue() == ((Integer)best.getValue()).intValue())
        bestAmbig = true;
    }
    if (best == null || bestAmbig)
      return null;
    ns = (String)best.getKey();
    lookupSourceUri(sourceUri).targetNamespace = ns;
    return ns;
  }

  private void inheritDownTargetNamespace(String sourceUri, String targetNamespace) {
    for (Iterator iter = lookupSourceUri(sourceUri).includes.iterator(); iter.hasNext();) {
      String uri = (String)iter.next();
      String ns = lookupSourceUri(uri).targetNamespace;
      if (ns == null) {
        ns = targetNamespace;
        lookupSourceUri(uri).targetNamespace = ns;
      }
      inheritDownTargetNamespace(uri, ns);
    }
  }

  private void chooseRootSchemas(SourceUriGenerator sug) {
    for (Iterator iter = targetNamespaceMap.entrySet().iterator(); iter.hasNext();) {
      Map.Entry entry = (Map.Entry)iter.next();
      String ns = (String)entry.getKey();;
      List list = new Vector();
      findRootSchemas(OutputDirectory.MAIN, ns, list);
      if (list.size() == 1)
        ((TargetNamespace)entry.getValue()).rootSchema = (String)list.get(0);
      else {
        String sourceUri = sug.generateSourceUri(ns);
        lookupSourceUri(sourceUri).includes.addAll(list);
        lookupSourceUri(sourceUri).targetNamespace = ns;
        ((TargetNamespace)entry.getValue()).rootSchema = sourceUri;
        schema.addInclude(sourceUri, null, null);
      }
    }
  }

  boolean isGlobal(Element element) {
    return element.equals(lookupElementName(element.getName()).globalType);
  }

  boolean isGlobal(Attribute attribute) {
    return attribute.equals(lookupAttributeName(attribute.getName()).globalType);
  }

  String getProxyName(Structure struct) {
    String ns = struct.getName().getNamespaceUri();
    TargetNamespace tn = lookupTargetNamespace(ns);
    String name = (String)tn.movedStructureNameMap.get(struct);
    if (name == null) {
      name = generateName(ns, tn, struct.getName().getLocalName(), struct instanceof Element);
      tn.movedStructureNameMap.put(struct, name);
    }
    return name;
  }

  String getOtherElementName(String ns) {
    TargetNamespace tn = lookupTargetNamespace(ns);
    if (!tn.movedOtherElement)
      return null;
    if (tn.otherElementName == null)
      tn.otherElementName = generateName(ns, tn, "local", true);
    return tn.otherElementName;
  }

  String getOtherAttributeName(String ns) {
    TargetNamespace tn = lookupTargetNamespace(ns);
    if (!tn.movedOtherAttribute)
      return null;
    if (tn.otherAttributeName == null)
      tn.otherAttributeName = generateName(ns, tn, "local", false);
    return tn.otherAttributeName;
  }

  private String generateName(String ns, TargetNamespace tn, String base, boolean isElement) {
    Set movedStructureNameSet = isElement ? tn.movedElementNameSet : tn.movedAttributeNameSet;
    String name = base;
    for (int n = 1;; n++) {
      if (!movedStructureNameSet.contains(name)) {
        Definition def;
        if (isElement)
          def = schema.getGroup(name);
        else
          def = schema.getAttributeGroup(name);
        if (def == null
            || !getTargetNamespace(def.getParentSchema().getUri()).equals(ns)
            || (def instanceof GroupDefinition
                && willElide((GroupDefinition)def)))
          break;
      }
      name = base + Integer.toString(n);
    }
    movedStructureNameSet.add(name);
    return name;
  }

  private boolean willElide(GroupDefinition def) {
    Particle particle = def.getParticle();
    if (!(particle instanceof Element))
      return false;
    return isGlobal((Element)particle);
  }

  String getTargetNamespace(String schemaUri) {
    return lookupSourceUri(schemaUri).targetNamespace;
  }

  boolean isTargetNamespace(String ns) {
    return targetNamespaceMap.get(ns) != null;
  }

  Set getTargetNamespaces() {
    return targetNamespaceMap.keySet();
  }

  String getRootSchema(String targetNamespace) {
    return lookupTargetNamespace(targetNamespace).rootSchema;
  }

  List getMovedStructures(String namespace) {
    return lookupTargetNamespace(namespace).movedStructures;
  }

  List effectiveIncludes(String sourceUri) {
    String ns = getTargetNamespace(sourceUri);
    List list = new Vector();
    for (Iterator iter = lookupSourceUri(sourceUri).includes.iterator(); iter.hasNext();)
      findRootSchemas((String)iter.next(), ns, list);
    return list;
  }

  private void findRootSchemas(String sourceUri, String ns, List list) {
    if (getTargetNamespace(sourceUri).equals(ns))
      list.add(sourceUri);
    else {
      for (Iterator iter = lookupSourceUri(sourceUri).includes.iterator(); iter.hasNext();)
        findRootSchemas((String)iter.next(), ns, list);
    }
  }

  private SourceUri lookupSourceUri(String uri) {
    SourceUri s = (SourceUri)sourceUriMap.get(uri);
    if (s == null) {
      s = new SourceUri();
      sourceUriMap.put(uri, s);
    }
    return s;
  }

  private TargetNamespace lookupTargetNamespace(String ns) {
    TargetNamespace t = (TargetNamespace)targetNamespaceMap.get(ns);
    if (t == null) {
      t = new TargetNamespace();
      targetNamespaceMap.put(ns, t);
    }
    return t;
  }

  private NameInfo lookupElementName(Name name) {
    NameInfo info = (NameInfo)elementNameMap.get(name);
    if (info == null) {
      info = new NameInfo();
      elementNameMap.put(name, info);
    }
    return info;
  }

  private NameInfo lookupAttributeName(Name name) {
    NameInfo info = (NameInfo)attributeNameMap.get(name);
    if (info == null) {
      info = new NameInfo();
      attributeNameMap.put(name, info);
    }
    return info;
  }

  static String otherNamespace(Wildcard wc) {
    if (wc.isPositive())
      return null;
    Set namespaces = wc.getNamespaces();
    switch (namespaces.size()) {
    case 2:
      if (!namespaces.contains(""))
        return null;
      Iterator iter = namespaces.iterator();
      String ns = (String)iter.next();
      if (!ns.equals(""))
        return ns;
      return (String)iter.next();
    case 1:
      if (namespaces.contains(""))
        return "";
    }
    return null;
  }

}