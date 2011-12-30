HrWrapSynth : HadronPlugin
{
	var <synthInstance, sliders, numBoxes, setFunctions, synthBusArgs, startButton, storeArgs, specs;
	var synthDesc, ctlNameStrings;
	var sName;
	
	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
			
		var numIns, numOuts, bounds, name = "HrWrapSynth", numControls;
		var synthDesc, ctlNameStrings;  // class method: instance vars are not accessible here
		
		if(argExtraArgs.size == 0, 
		{ 
			argParentApp.displayStatus("This plugin requires an argument. See HrWrapSynth help.", -1);
			this.halt; 
		});
		
		synthDesc = SynthDescLib.global.synthDescs.at(argExtraArgs[0].asSymbol);
		if(synthDesc == nil,
		{
			argParentApp.displayStatus("SynthDef"+argExtraArgs[0]+"not found in global SynthDescLib. See HrWrapSynth help.", -1);
			this.halt; 
		});
		
		if(synthDesc.metadata == nil,
		{
			argParentApp.displayStatus("You need to supply metadata and specs for your synth. See HrWrapSynth help.", -1);
			this.halt; 
		});
		
		// no, fix this...
		ctlNameStrings = Array(synthDesc.controlNames.size);
		synthDesc.controls.do { |cn, i|
			var name = cn.name.asSymbol;
			if(name != '?' and: { synthDesc.symIsArrayArg(name).not }) {
				ctlNameStrings.add(cn.name.asString);
			};
		};
		numControls = ctlNameStrings.size; // synthDesc.metadata.at(\specs).size;
		
		numIns = ctlNameStrings.count({|item| item.asString.find("inBus", true, 0) == 0; });
		numOuts = ctlNameStrings.count({|item| item.asString.find("outBus", true, 0) == 0; });
		
		bounds = Rect(400, 400, 350, 50 + (numControls * 30));
		
		^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init(ctlNameStrings, synthDesc);
	}
	
	init
	{	|ctlstr, sdesc|
		var sdControls;
		ctlNameStrings = ctlstr;
		synthDesc = sdesc;
		sliders = List.new;
		numBoxes = List.new;
		setFunctions = List.new;
		storeArgs = Dictionary.new;
		
		helpString = "This plugin reads a SynthDef from SynthDescLib.default and integrates it with the Hadron system.";	
				
		synthBusArgs = 
		{
			inBusses.collect({|item, cnt| [("inBus"++cnt).asSymbol, inBusses[cnt]] }).flatten ++
			outBusses.collect({|item, cnt| [("outBus"++cnt).asSymbol, outBusses[cnt]] }).flatten;
		};
		
		sName = extraArgs[0].asSymbol;
		
		window.background_(Color(0.9, 1, 0.9));
		
		specs = synthDesc.metadata.at(\specs);
		
		//keeping relevant args
		sdControls = ctlNameStrings.reject
		({|item|
			(item.find("inBus", true, 0) == 0) or: {
				item.find("outBus", true, 0) == 0
				or: {  // block array args from gui
					synthDesc.symIsArrayArg(item.asSymbol)
				}
			}
		});
		
		//drawing gui
		sdControls.do
		({|item, count|
			var default = synthDesc.controls.detect({ |cn|
				cn.name.asString == item
			}).defaultValue;
			
			StaticText(window, Rect(10, 10 + (count * 30), 80, 20)).string_(item);
			
			storeArgs.put(item.asSymbol, specs.at(item.asSymbol).unmap(default));
			
			numBoxes.add
			(
				NumberBox(window, Rect(200, 10 + (count * 30), 80, 20))
				.value_(/*specs.at(item.asSymbol).*/default)
				.action_({|num| sliders[count].valueAction_(specs.at(item.asSymbol).unmap(num.value)); });
			);
			
			sliders.add
			(
				HrSlider(window, Rect(90, 10 + (count * 30), 100, 20))
				.value_(specs.at(item.asSymbol).unmap(/*specs.at(item.asSymbol).*/default))
				.action_
				({|sld| 
					
					setFunctions[count].value(sld.value);
				});
			);
			
			setFunctions.add
			({|val|
				
				var mapped = specs.at(item.asSymbol).map(val.value);
				storeArgs.put(item.asSymbol, val);
				synthInstance.set(item, mapped);
				{ numBoxes[count].value_(mapped); }.defer;
			});
			
			//add the modulatable entry for the control
			modGets.put(item.asSymbol, { storeArgs.at(item.asSymbol); });
			modSets.put(item.asSymbol, {|argg| setFunctions[count].value(argg); { sliders[count].value_(argg); }.defer; });
			
			
		});
		
		startButton = 
		Button(window, Rect(10, 10 + (30 * sliders.size), 80, 20))
		.states_([["Start", Color.black, Color(0.5, 0.7, 0.5)], ["Stop", Color.white, Color(0.7, 0.5, 0.5)]])
		.value_(1)
		.action_
		({|btn|
			btn.value.switch
			(
				0, { this.releaseSynth },
				1, { this.makeSynth }
			)
		});
		
		saveGets = 
			sliders.collect({|item, cnt| [{ sliders[cnt].value; }, { sliders[cnt].boundMidiArgs; }, { sliders[cnt].automationData }]; }).flat ++
			[ { startButton.value; }; ];
		
		saveSets =
			sliders.collect
			({|item, cnt| 
				[
					{|argg| sliders[cnt].valueAction_(argg); }, 
					{|argg| sliders[cnt].boundMidiArgs_(argg); }, 
					{|argg| sliders[cnt].automationData_(argg) }
				]; 
			}).flat ++  [ {|argg| startButton.valueAction_(argg); }; ];
		this.makeSynth;
	}

	releaseSynth {
		if(synthInstance.notNil) {
			if(synthDesc.tryPerform(\hasGate) ? false) { synthInstance.release }
			{ synthInstance.free };
			synthInstance = nil;
		};
	}

	makeSynth {
		var tempArgs;
		this.releaseSynth;
		tempArgs = (synthBusArgs.value ++ storeArgs.keys.collect({|item| [item, specs.at(item.asSymbol).map(storeArgs.at(item))]; }).asArray).flatten(1);
		synthInstance = Synth(sName, tempArgs, target: group);
	}

	updateBusConnections
	{
		synthInstance.set(*synthBusArgs.value);
	}
	
	cleanUp
	{
		this.releaseSynth;
	}
}