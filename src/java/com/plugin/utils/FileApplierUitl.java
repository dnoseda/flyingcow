package com.plugin.utils;

import groovy.lang.Closure;

import java.io.File;
import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

public class FileApplierUitl {
	
	public static List<String> applyFunc(List<String> fileNames, Closure closure){
		List<String> result = Lists.newArrayList();
		for(String fileName: fileNames){
			result.addAll(applyFunc(new File(fileName),closure));
		}
		return result;
	}
	private static List<String> applyFunc(File file, Closure closure){
		Preconditions.checkNotNull(file);
		Preconditions.checkNotNull(closure);
		Preconditions.checkArgument(file.exists());
		List<String> result = Lists.newArrayList();
		if(file.isDirectory()){
			applyFuncDirectory(file,closure,result);
		}else{
			applyFuncFile(file,closure,result);
		}
		return result;		
	}

	private static void applyFuncFile(File file, Closure closure,
			List<String> appendTo) {
		Preconditions.checkArgument(file.isFile(), "[file] must be a file");
		appendTo.add(String.valueOf(closure.call(file)));
	}

	private static void applyFuncDirectory(File directory, Closure closure,
			List<String> appendTo) {
		Preconditions.checkArgument(directory.isDirectory(), "[directory] must be a file");
		for(File file: directory.listFiles()){
			if(file.isDirectory()){
				applyFuncDirectory(file, closure, appendTo);
			}else{
				applyFuncFile(file, closure, appendTo);
			}
		}
	}
}
