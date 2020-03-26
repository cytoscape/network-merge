package org.cytoscape.network.merge.internal.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/*
 * #%L
 * Cytoscape Merge Impl (network-merge-impl)
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2006 - 2013 The Cytoscape Consortium
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as 
 * published by the Free Software Foundation, either version 2.1 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */

import org.cytoscape.model.CyColumn;
import org.cytoscape.model.CyEdge;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNode;
import org.cytoscape.model.CyRow;
import org.cytoscape.model.CyTable;
import org.cytoscape.network.merge.internal.EdgeMerger;
import org.cytoscape.network.merge.internal.EdgeSpec;
import org.cytoscape.network.merge.internal.Merge;
import org.cytoscape.network.merge.internal.NodeMerger;
import org.cytoscape.network.merge.internal.task.ColumnMergeRecord;
import org.cytoscape.network.merge.internal.util.ColumnType;

/**
 * Class to store the information how to map the attributes 
 * in the original networks map to those in the resulting network
 * 
 * 
 */
final public class AttributeMap {
    private Map<CyNetwork,List<String>> attributeMapping; // a map of network to list of attributes
    private List<String> mergedAttributes;
    private List<ColumnType> mergedAttributeTypes;
    private List<Boolean> mergedAttributeMutability;
    private Map<CyNetwork,CyTable> cyTables;
    private final String nullAttr = ""; // to hold a position in vector standing that it's not a attribute

    public void dump(String s)
    {
    	if (Merge.verbose == false) return;
    	System.out.println("AttributeMap " + s + ": ");
    	for (CyNetwork net : attributeMapping.keySet())
    	{
    		System.out.print(net.getSUID());
    		System.out.println(attributeMapping.get(net));
    	}
		System.out.println(mergedAttributes);
		System.out.println(mergedAttributeTypes);
		System.out.println(mergedAttributeMutability);

//    	System.out.print(dumpStrList(mergedAttributes));
   }
    public static String dumpStrList(List<String> list) {
		StringBuilder str = new StringBuilder("[");
		for (String s : list)
			str.append(s + ",");
		str.append("]");
		return str.toString();
	}
    public static String dumpStrs(String[] a) {
		StringBuilder str = new StringBuilder("[");
		for (String s : a)
			str.append(s + ",");
		str.append("]");
		return str.toString();
	}
    //-------------------------------------------------------------------
	public AttributeMap() {
        attributeMapping = new HashMap<CyNetwork,List<String>>();
        mergedAttributes = new ArrayList<String>();
        mergedAttributeTypes = new ArrayList<ColumnType>();
        mergedAttributeMutability = new ArrayList<Boolean>();
        cyTables = new WeakHashMap<CyNetwork,CyTable>();
    }


    public CyTable getCyTable(CyNetwork net){    return cyTables.get(net);    }
    public String[] getMergedAttributes() 	{     return (String[])mergedAttributes.toArray(new String[0]);  }
    public int getSizeMergedAttributes() 	{     return mergedAttributes.size();    }
        
    public String getMergedAttribute(final int index) {
    if (index<0 || index>=getSizeMergedAttributes()) 
        throw new IndexOutOfBoundsException("Index out of boundary.");        
        return mergedAttributes.get(index);
    }
  
    public void setMergedAttributeAndType(final int index, final String attributeName, ColumnType type) {
    	setMergedAttribute( index, attributeName);
    	setMergedAttributeType( index, type);   	
    }
    
    public String setMergedAttribute(final int index, final String attributeName) {
        if (attributeName==null)   throw new NullPointerException("Column name is null.");
        String ret = mergedAttributes.set(index, attributeName);
        resetMergedAttributeType(index,false);
        return ret;
    }

    public ColumnType getMergedAttributeType(final int index) {
        if (index>=this.getSizeMergedAttributes()||index<0)    throw new IndexOutOfBoundsException();
        return mergedAttributeTypes.get(index);
    }

    public ColumnType getMergedAttributeType(final String mergedAttributeName) {
        if (mergedAttributeName==null)      throw new NullPointerException("Null netID or mergedAttributeName");

        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1)     throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");

        return getMergedAttributeType(index);
    }

    public boolean  setColumnMerge(int index, ColumnMergeRecord rec)
    {
    	String s = setMergedAttribute(index, rec.outName);
		boolean ok = setMergedAttributeType(index, rec.outType);
		return ok && s != null;
    }
    
    
    public boolean setMergedAttributeType(int index, ColumnType type) {
        if (index>=this.getSizeMergedAttributes()||index<0)      throw new IndexOutOfBoundsException();

        Map<CyNetwork,String> map = getOriginalAttributeMap(index);
        for (Map.Entry<CyNetwork,String> entry : map.entrySet()) {
            CyTable table = cyTables.get(entry.getKey());
            ColumnType oriType = ColumnType.getType(table.getColumn(entry.getValue()));
            if (!ColumnType.isConvertable(oriType, type)) {
                System.err.println("Cannot convert from "+oriType.name()+" to "+type.name());
                return false;
            }
        }

        this.mergedAttributeTypes.set(index, type);
        return true;
    }

    
    public boolean setMergedAttributeType(String mergedAttributeName, ColumnType type) {
        if (mergedAttributeName==null)    throw new NullPointerException("Null netID or mergedAttributeName");
 
        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1)      			throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
        return setMergedAttributeType(index,type);
    }

    public boolean getMergedAttributeMutability(final int index) {
        return mergedAttributeMutability.get(index);
    }

    
    public boolean getMergedAttributeMutability(final String mergedAttributeName) {
        if (mergedAttributeName==null) {
            throw new NullPointerException("Null netID or mergedAttributeName");
        }

        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1) 
            throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
         return getMergedAttributeMutability(index);
    }

    
    public void setMergedAttributeMutability(int index, boolean isImmutable) {
        if (index>=this.getSizeMergedAttributes()||index<0)    throw new IndexOutOfBoundsException();
        mergedAttributeMutability.set(index, isImmutable);
    }

    
    public void setMergedAttributeMutability(String mergedAttributeName, boolean isImmutable) {
        if (mergedAttributeName==null)        throw new NullPointerException("Null netID or mergedAttributeName");
       
        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1)   throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
        setMergedAttributeMutability(index,isImmutable);
    }
            
    
    public boolean containsMergedAttribute(final String attributeName) {
        if (attributeName==null)      throw new NullPointerException("Column name is null.");
        return mergedAttributes.contains(attributeName);
    }
    
    
    public String getOriginalAttribute(final CyNetwork net, final String mergedAttributeName) {
        if (net==null||mergedAttributeName==null)     	throw new NullPointerException("Null netID or mergedAttributeName");
        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1)          						throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
        return getOriginalAttribute(net, index);
    }
    
    
    public String getOriginalAttribute(final CyNetwork net, final int index) {
        final List<String> attrs = attributeMapping.get(net);
        if (attrs==null)             		throw new IllegalArgumentException(net+" is not selected as merging network");
        if (index>=attrs.size()||index<0)   throw new IndexOutOfBoundsException();
        final String attr = attrs.get(index);
        if (attr.compareTo(nullAttr)==0) return null;
        return attr;
    }
 

    public Map<CyNetwork,String> getOriginalAttributeMap(String mergedAttributeName) {
        if (mergedAttributeName==null) 
            throw new NullPointerException("Null netID or mergedAttributeName");

        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1) 
            throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
       
        return getOriginalAttributeMap(index);        
    }
    
    
    public Map<CyNetwork,String> getOriginalAttributeMap(int index) {
        if (index>=this.getSizeMergedAttributes()||index<0)        throw new IndexOutOfBoundsException();
        Map<CyNetwork,String> netStrMap = new HashMap<CyNetwork,String>();
        
        final Iterator<Map.Entry<CyNetwork,List<String>>> it = attributeMapping.entrySet().iterator();
        while (it.hasNext()) {
            final Map.Entry<CyNetwork,List<String>> entry = it.next();
            final CyNetwork net = entry.getKey();
            final List<String> attrs = entry.getValue();
            final String attr = attrs.get(index);
            if (attr.compareTo(nullAttr)!=0)         
            	netStrMap.put(net, attr);
        }
        
        return netStrMap;
    }
    
    
    public String setOriginalAttribute(final CyNetwork net, final String attributeName, final String mergedAttributeName) {
        if (net==null||mergedAttributeName==null) {
            throw new NullPointerException("Null netID or mergedAttributeName");
        }
        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1) {
            throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
        }
        return setOriginalAttribute(net, attributeName, index);
    }
            
    
    public String setOriginalAttribute(final CyNetwork net, final String attributeName, final int index){
        if (net==null||attributeName==null||attributeName==null) {
            throw new NullPointerException("Null netID or attributeName or mergedAttributeName");
        }
        
        final List<String> attrs = attributeMapping.get(net);
        if (attrs==null) return null;
        if (index>=attrs.size()||index<0) {
            throw new IndexOutOfBoundsException();
        }
        
        final String old = attrs.get(index);
        if (old.compareTo(attributeName)!=0) { // not the same                     
            attrs.set(index, attributeName);
            resetMergedAttributeType(index,false);
        }

        return old;
    }
    
    
    public String removeOriginalAttribute(final CyNetwork net, final String mergedAttributeName) {
        if (net==null||mergedAttributeName==null)       throw new NullPointerException("Null netID or mergedAttributeName");
        
        final int index = mergedAttributes.indexOf(mergedAttributeName);
        if (index==-1)       throw new IllegalArgumentException("No "+mergedAttributeName+" is contained in merged table columns");
        return removeOriginalAttribute(net, index);
    }
    
    
    public String removeOriginalAttribute(final CyNetwork net, final int index) {
        if (net==null) {
            throw new NullPointerException("Null netID");
        }
        
        if (index<0 || index>=getSizeMergedAttributes()) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        
        final List<String> attrs = attributeMapping.get(net);
        
        String old = attrs.set(index, nullAttr);
        if (!pack(index)) {
                this.resetMergedAttributeType(index,false);
        }
        
        return old;
    }

    
    public String removeMergedAttribute(final String mergedAttributeName) {
        if (mergedAttributeName==null)         throw new NullPointerException("Null mergedAttributeName");
        final int index = mergedAttributes.indexOf(mergedAttributeName);
        return (index ==-1 )? null: removeMergedAttribute(index);
    }
    
    
    public String removeMergedAttribute(final int index) {
        if (index<0 || index>=getSizeMergedAttributes())     throw new IndexOutOfBoundsException("Index out of bounds");
         for (List<String> attrs : attributeMapping.values()) 
                attrs.remove(index);
        mergedAttributeTypes.remove(index);
        mergedAttributeMutability.remove(index);
        
        return mergedAttributes.remove(index);
    }
    
    
    public String addAttributes(final Map<CyNetwork,String> mapNetAttributeName, final String mergedAttrName) {
        return addAttributes(mapNetAttributeName,mergedAttrName,getSizeMergedAttributes());
    }
    
    
    public String addAttributes(final Map<CyNetwork,String> mapNetAttributeName, final String mergedAttrName, final int index) {
        if (mapNetAttributeName==null || mergedAttrName==null) throw new NullPointerException();
        if (index<0 || index>getSizeMergedAttributes())        throw new IndexOutOfBoundsException("Index out of bounds");
//        if (mapNetAttributeName.isEmpty())             			throw new IllegalArgumentException("Empty map");
        
        final Set<CyNetwork> networkSet = getNetworkSet();
        if (!networkSet.containsAll(mapNetAttributeName.keySet())) throw new IllegalArgumentException("Non-exist network(s)");
        
//        final Iterator<Map.Entry<CyNetwork,List<String>>> it = attributeMapping.entrySet().iterator();
        //final Iterator<Vector<String>> it = attributeMapping.values().iterator();
        for (CyNetwork net : networkSet)  // add an empty attr for each network
        {
            final List<String> attrs = attributeMapping.get(net);
            String name = mapNetAttributeName.get(net);
            if (name != null)  attrs.add(index,name);
            else               attrs.add(index,nullAttr);
        }
        
        String defaultName = getDefaultMergedAttrName(mergedAttrName);
        mergedAttributes.add(index,defaultName);// add in merged attr
        resetMergedAttributeType(index, true);
        resetMergedAttributeMutability(index, true);
        return defaultName;
    }

    
    public void addNetwork(final CyNetwork net, CyTable table) {
        if (net==null || table==null)
            throw new NullPointerException();

        cyTables.put(net, table);
        
        final List<String> attributeNames = new ArrayList<String>();
        for (CyColumn col : table.getColumns()) {
            String colName = col.getName();
            if (!colName.equals("SUID") && !colName.equals("selected"))  //skip SUID & selected
                attributeNames.add(col.getName());
        }
        Collections.sort(attributeNames);

        final int nAttr = attributeNames.size();
        if (attributeMapping.isEmpty()) { // for the first network added
            
            final List<String> attrs = new ArrayList<String>();
            attributeMapping.put(net, attrs);
            for (int i=0; i<nAttr; i++) 
                addNewAttribute(net, attributeNames.get(i));
 
        } else { // for each attributes to be added, search if the same attribute exists
                 // if yes, add to that group; otherwise create a new one
            List<String> attrs = attributeMapping.get(net);
            if (attrs!=null) { // this network already exist
                System.err.println("Error: this network already exists");
                return;
            }

            final int nr = mergedAttributes.size(); // # of rows, the same as the # of attributes in merged network
            attrs = new ArrayList<String>(nr); // new map
            for (int i=0; i<nr; i++) 
                attrs.add(nullAttr);
            
            attributeMapping.put(net, attrs);

            for (int i=0; i<nAttr; i++) {
                final String at = attributeNames.get(i);
                 
                boolean found = false;             
                for (int ir=0; ir<nr; ir++) {
                    if (attrs.get(ir).compareTo(nullAttr)!=0) continue; // if the row is occupied
                    if (mergedAttributes.get(ir).compareTo(at)==0) { // same name as the merged attribute
                        found = true;
                        this.setOriginalAttribute(net, at, ir);
                        break; 
                    }

                   for (CyNetwork net_curr : attributeMapping.keySet())
                   {
                        final String attr_curr = attributeMapping.get(net_curr).get(ir);
                        if (attr_curr.compareTo(at)==0)  // same name as the original attribute
                        {
                        	found = true;
                            this.setOriginalAttribute(net, at, ir);
                            break; 
                        }
                    }
                }

                if (!found)  //no same attribute found
                    addNewAttribute(net,at);
            }
        }
    }
    
    
    public Set<CyNetwork> getNetworkSet() {   return attributeMapping.keySet();    }

    
    public int size()		 {        return attributeMapping.size();    }   
    
    
    public void removeNetwork(final CyNetwork net) {
        if (net==null) 
            throw new NullPointerException("net == null");

        final List<String> removed = attributeMapping.remove(net);
        final int n = removed.size();
        for (int i=n-1; i>=0; i--) {
            if (removed.get(i).compareTo(nullAttr)!=0) { // if the attribute is not empty
                if (!pack(i)) { // if not removed
                        this.resetMergedAttributeType(i, false);
                        this.resetMergedAttributeMutability(i, false);
                }
            }
        }
    }
    
    /**
     * {@inheritDoc}
     */
    protected boolean pack(final int index) {
        if (index<0 || index>=getSizeMergedAttributes()) 
            throw new IndexOutOfBoundsException("Index out of boundary.");
         
        Iterator<List<String>> it = attributeMapping.values().iterator();
        while (it.hasNext())
            if (it.next().get(index).compareTo(nullAttr)!=0) 
                return false;

        this.removeMergedAttribute(index);
        return true;
    }
    
    private String getDefaultMergedAttrName(final String attr) 
    {
        if (attr==null)     throw new NullPointerException("attr==null");
        
        String appendix = "";
        for (int i = 0; true; ) {
            String attr_ret = attr+appendix;
            if (mergedAttributes.contains(attr_ret)){
                appendix = "." + ++i;
            } else  return attr+appendix;
        }
    }
        
    protected void addNewAttribute(final CyNetwork net, final String attributeName) {
        if (net==null || attributeName==null)  throw new NullPointerException();
        
        final Iterator<List<String>> it = attributeMapping.values().iterator();
        while (it.hasNext()) // add an empty attr for each network
            it.next().add(nullAttr);

        final List<String> attrs = attributeMapping.get(net);
        attrs.set(attrs.size()-1, attributeName); // set attr
        
        String attrMerged = attributeName;
        mergedAttributes.add(getDefaultMergedAttrName(attrMerged)); // add in merged attr
        this.resetMergedAttributeType(mergedAttributeTypes.size(),true);
        this.resetMergedAttributeMutability(mergedAttributeMutability.size(),true);
    }

    protected void resetMergedAttributeType(final int index, boolean add) {
        if (this.getSizeMergedAttributes()>this.mergedAttributeTypes.size()+(add?1:0)) 
                throw new IllegalStateException("column type not complete");

        if (index>=this.getSizeMergedAttributes()||index<0) 
                throw new IndexOutOfBoundsException();

        Map<CyNetwork,String> map = getOriginalAttributeMap(index);
        Set<ColumnType> types = EnumSet.noneOf(ColumnType.class);
        for (Map.Entry<CyNetwork,String> entry : map.entrySet()) {
            CyTable table = cyTables.get(entry.getKey());
            types.add(ColumnType.getType(table.getColumn(entry.getValue())));
     }
        
     final ColumnType type = ColumnType.getReasonableCompatibleConversionType(types);

        if (add) //new
                mergedAttributeTypes.add(index,type);
        else {
            final ColumnType old = mergedAttributeTypes.get(index);
            if (!ColumnType.isConvertable(type, old))
                mergedAttributeTypes.set(index, type);
        }
    }

    protected void resetMergedAttributeMutability(final int index, boolean add) {
        if (this.getSizeMergedAttributes()>this.mergedAttributeMutability.size()+(add?1:0)) 
                throw new IllegalStateException("column mutability not complete");
        if (index>=this.getSizeMergedAttributes()||index<0)
                throw new IndexOutOfBoundsException();

        Map<CyNetwork,String> map = getOriginalAttributeMap(index);
        boolean isImmutable = true;
        for (Map.Entry<CyNetwork,String> entry : map.entrySet()) {
            CyTable table = cyTables.get(entry.getKey());
            if (table.getColumn(entry.getValue()).isImmutable())
              continue;
            isImmutable = false;
            break;
        }
        if (add)  // new
            mergedAttributeMutability.add(index, isImmutable);
        else
            mergedAttributeMutability.set(index, isImmutable);
       
    }

	
}
