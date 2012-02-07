HrWrapSynth : HadronPlugin
{
	var sliders, numBoxes, setFunctions, synthBusArgs, startButton, storeArgs, specs;
	var synthDesc, ctlNameStrings;
	var sName;
	
	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
			
		var numIns, numOuts, bounds, name = "HrWrapSynth", numControls;
		var synthDesc, ctlNameStrings;  // class method: instance vars are not accessible here
		var err, continue = true;
		var specTemp;
		
		if(argExtraArgs.size == 0, 
		{
			err = Error("This plugin requires an argument. See HrWrapSynth help.");
			argParentApp.displayStatus(err.errorString, -1);
			err.throw;
		});
		
		synthDesc = SynthDescLib.global.synthDescs.at(argExtraArgs[0].asSymbol);
		if(synthDesc == nil,
		{
			err = Error("SynthDef"+argExtraArgs[0]+"not found in global SynthDescLib. See HrWrapSynth help.");
			argParentApp.displayStatus(err.errorString, -1);
			err.throw;
		});
		
		if(synthDesc.metadata == nil,
		{
			err = Error("You need to supply metadata and specs for your synth. See HrWrapSynth help.");
			argParentApp.displayStatus(err.errorString, -1);
			err.throw;
		});

		if(continue) {
			// no, fix this...
			ctlNameStrings = Array(synthDesc.controlNames.size);
			specTemp = synthDesc.metadata[\specs] ?? { IdentityDictionary.new };
			synthDesc.controls.do { |cn, i|
				var name = cn.name.asSymbol;
				if(specTemp[name].notNil and: {
					name != '?' and: { synthDesc.symIsArrayArg(name).not }
				}) {
					ctlNameStrings.add(cn.name.asString);
				};
			};
			numControls = ctlNameStrings.size; // synthDesc.metadata.at(\specs).size;
			
			numIns = synthDesc.controlNames.count({ |item|
				item.asString.find("inBus", true, 0) == 0
			});
			numOuts = synthDesc.controlNames.count({ |item|
				item.asString.find("outBus", true, 0) == 0
			});
			
			bounds = Rect(400, 400, 350, 50 + (numControls * 30));
			
			^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init(ctlNameStrings, synthDesc);
		} { ^nil }
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
			outBusses.collect({|item, cnt| [("outBus"++cnt).asSymbol, outBusses[cnt]] }).flatten
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

			item = item.asSymbol;

			StaticText(window, Rect(10, 10 + (count * 30), 80, 20)).string_(item);
			
			storeArgs.put(item, specs.at(item).unmap(default));
			
			numBoxes.add
			(
				NumberBox(window, Rect(200, 10 + (count * 30), 80, 20))
				.value_(default)
				.action_({|num| sliders[count].valueAction_(specs.at(item).unmap(num.value)); });
			);
			
			sliders.add
			(
				HrSlider(window, Rect(90, 10 + (count * 30), 100, 20))
				.value_(specs.at(item).unmap(default))
				.action_
				({|sld| 
					setFunctions[count].value(specs.at(item).map(sld.value));
				});
			);
			
			setFunctions.add
			({|val|
				// var mapped = specs.at(item).map(val.value);  // why .value here?
				// storeArgs is normalized (compatibility with old saved patches)
				storeArgs.put(item, specs.at(item).unmap(val));
				synthInstance.set(item, val);
				{ numBoxes[count].value_(val); }.defer;
			});
			
			// add the modulatable entry for the control
			// should be REAL values, not normalized
			modGets.put(item, { specs[item].map(storeArgs[item]); });
			modSets.put(item, {|argg| setFunctions[count].value(argg); { sliders[count].value_(storeArgs[item]); }.defer; });

			modMapSets.put(item, { |argg|
				storeArgs.put(item, specs.at(item).unmap(argg));
				{
					sliders[count].value = storeArgs[item];
					numBoxes[count].value = argg;
				}.defer;
			});
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

	makeSynth {
		this.releaseSynth;
		if(polyMode.not) {
			synthInstance = Synth(sName, this.synthArgs, target: group);
		};
	}

	synthArgs {
		^(
			synthBusArgs.value
			++ storeArgs.keys.collect({ |item|
				[item, specs.at(item.asSymbol).map(storeArgs.at(item))]
			}).asArray
		).flatten(1) ++ this.getMapModArgs;
	}

	updateBusConnections
	{
		synthInstance.set(*synthBusArgs.value);
	}
	
	cleanUp
	{
		this.releaseSynth;
	}

	hasGate { ^(synthDesc.tryPerform(\hasGate) ? false) }
	polySupport { ^true }
	defName { ^synthDesc.name }
}