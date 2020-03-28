package org.cytoscape.network.merge.internal.model;

import java.util.ArrayList;
import java.util.List;

import org.cytoscape.model.CyNetwork;

public class MergeMap 
{
	List<CyNetwork> sourceNetworks;
	public List<ColumnMergeRecord> columnsToMerge = new ArrayList<ColumnMergeRecord>();
	boolean verbose = false;
	
	public MergeMap(String s, List<CyNetwork> nets)
	{
		if (s == null) 	{	if (verbose) System.out.println("no merge map defined"); return;		}
		if (verbose) 	System.out.println("MergeMap: " + s + ", netsize= " + nets.size());
		sourceNetworks = nets;
		int ptr = 0;
		while (ptr >= 0)		// parse "{ a, b }" into columnsToMerge list
		{
			int start = s.indexOf('{', ptr);
			int end = s.indexOf('}', start+1);
			if (start >= 0 && end > 0)
			{
				String chunk = s.substring(start+1, end);
				ColumnMergeRecord rec = new ColumnMergeRecord(chunk, nets);
				columnsToMerge.add(rec);
			}
			ptr = s.indexOf(',', end+1);
		}
	}
	
	//--------------------------------------------
	public void dump() {
		for (ColumnMergeRecord rec : columnsToMerge)
			rec.dump();
	}
}

