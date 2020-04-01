package org.cytoscape.network.merge.internal.util;

import java.util.ArrayList;
import java.util.List;

import org.cytoscape.model.CyNetwork;
import org.cytoscape.network.merge.internal.model.ColumnMergeRecord;

public class ParseUtils
{
	public static List<ColumnMergeRecord> getMergeMap(String s, List<CyNetwork> nets)
	{
		List<ColumnMergeRecord> columnsToMerge = new ArrayList<ColumnMergeRecord>();
		if (s == null) 	{return null;}
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
		return columnsToMerge;
	}
}

