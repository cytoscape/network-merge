# Cytoscape Core App: Network Merge

## Introduction

This is a Cytoscape Core App for merging networks

## How to build

```bash
git clone https://github.com/cytoscape/network-merge.git
mvn clean install
```

## Test cases

Test cases are contained in the /src/test/resources directory. 
These include cases for:

* 1, 2, and 3 network merge for undirected/directed networks
* Single network merge for mixed network

There are three different types of merge - union, intersection, and difference. Union is available for 1, 2, and 3 network merges,  while intersection is available for all merges involving two or more networks. Difference is only applicable to two network merges. 

Within difference, there are two options - "Only remove nodes if all their edges are being subtracted, too"  and "Remove all nodes that are in the second network". Our test cases refer to these as Difference A and Difference B respectively.Also, our test cases include expected results for both possible difference merges - those being Network 1 - Network 2 and
Network 2 - Network 1.

Each test case is in its own session file - this includes the input and expected output networks for each merge type. To verify correct results for directed/undirected networks, you will need to run the appropriate merge on the input networks using Tools-Merge-Networks...  Then, export both the expected results and the actual results networks as SIF files (File-Export-Network...). 

Finally, sort both SIF files using Excel or similar such that source/target pairs are in the same order. For undirected networks, you will first need to use a macro of some sort to sort each row horizontally so that source-target pairs are in the same order. For mixed networks, you will need to verify by hand - note that directedness can only be seen in the network XGMML as of Cytoscape 3.4, and an undirected edge will never merge with a directed edge.

Once this is done, you can compare the two SIF files using diff (or similar). If you want to compare attribute output as well as network output, you can instead export an XGMML file and use Excel to sort that by source-target pairs in a similar fashion. However, there are known issues with attribute merge, so the expected results here may not match the actual results.

TODO: Automate test case verification using JUnit tests.

