HadronStateLoad
{
	var parentApp, loadStage;
	var <action;  // on completion
	
	*new
	{|argParentApp|
		
		^super.new.init(argParentApp);
	}
	
	init
	{|argParentApp|
	
		parentApp = argParentApp;
		loadStage = 0;
	}
	
	showLoad
	{
		File.openDialog("", {|aFile|
			if(aFile.isArray and: aFile.isString.not) {
				aFile = aFile.first;
			};
			parentApp.alivePlugs.size.do({ parentApp.alivePlugs[0].selfDestruct; });
			this.loadState(aFile);
		});
	}
	
	loadState
	{|argFile|
		
		var contents;
		var tempFile = File(argFile, "r");
		var tempPlug, tempThing;
		
		contents = tempFile.readAllString().split($\n);
		tempFile.close;
		
		
		
		
		{//begin fork
		contents.do
		({|item|
			
			if(item == "?EndPlugs", { loadStage = 3; Hadron.loadDelay.wait; });
			if(item == "?EndConnections", { loadStage = 5; });
			if(item == "?EndPlugParams", { loadStage = 7; });
			if(item == "?EndSave", { loadStage = 8; });
			
			if(loadStage == 2, 
			{ 
				//item.postln; 
				item = item.split(31.asAscii);
				parentApp.prAddPlugin(item[0].interpret, item[1], item[2].asInteger, item[3].interpret, item[4].interpret);
				
				if(GUI.id != \swing) { 0.01.wait };
				parentApp.idPlugDict.at(item[2].asInteger)
					.outerWindow.bounds = item[5].interpret;
				
				parentApp.idPlugDict.at(item[2].asInteger)
					.oldWinBounds = item[6].interpret;
				
				if(GUI.id != \swing) { 0.05.wait };
				parentApp.idPlugDict.at(item[2].asInteger).isHidden = item[7].interpret;
			});
			
			if(loadStage == 4,
			{
				item = item.split(31.asAscii);
				tempPlug = parentApp.idPlugDict.at(item[0].interpret);
				tempThing = item[1].interpret;
				tempPlug.inConnections = Array.fill(tempThing.size/*, { [nil, nil] }*/);
					
				tempThing.do { |inItem, i|
					tempPlug.setInputConnection(i, 
						if(inItem[0] != nil, 
							{ [parentApp.idPlugDict.at(inItem[0]), inItem[1]]},
							{ inItem; });
					);
				};
				
				tempThing = item[2].interpret;
				tempPlug.outConnections = Array.fill(tempThing.size/*, { [nil, nil] }*/);
				tempThing.do { |outItem, i|
					tempPlug.setOutputConnection(i, 
						if(outItem[0] != nil, 
							{ [parentApp.idPlugDict.at(outItem[0]), outItem[1]]},
							{ outItem; });
					);
				};
				// tempPlug.outConnections = Array.fill(tempThing.size/*, { [nil, nil] }*/);
				// 	item[2].interpret.collect
				// 	({|outItem| 
				// 		if(outItem[0] != nil, 
				// 		{ [parentApp.idPlugDict.at(outItem[0]), outItem[1]]},
				// 		{ outItem; }); 
				// 	});
			});
			
			if(loadStage == 5,
			{
				parentApp.alivePlugs.do({|plug| plug.wakeConnections; });
				// the other "if" loadStages have to process multiple lines
				// but this one must go once and only once
				loadStage = nil;
			});
			
			if(loadStage == 6,
			{
			
				item = item.split(31.asAscii);
				parentApp.idPlugDict.at(item[0].interpret).injectSaveValues(item[1..]);
			});
			
			if(loadStage == 7,
			{
				parentApp.alivePlugs.do(_.wakeFromLoad);
				0.25.wait;
				parentApp.alivePlugs.do(_.prUpdateBusConnections);
				parentApp.canvasObj.drawCables;
				parentApp.isDirty = false;
				parentApp.displayStatus("State loaded!", 1);

				action.value(parentApp);
			});
			
			
			
			if(item == "?Hadron 1", { loadStage = 1; });
			if(item == "?StartPlugs", { loadStage = 2; });
			if(item == "?StartConnections", { loadStage = 4; });
			if(item == "?StartPlugParams", { loadStage = 6; });
			
		});
		
		}.fork(AppClock);
		
	}
	
	addFunc { |function|
		action = action.addFunc(function);
	}

	removeFunc { |function|
		action = action.removeFunc(function);
	}
	
}