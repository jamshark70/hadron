HrStereoMixer : HadronPlugin
{
	var synthInstances, summerSynth, sourceSlider, parNumIns, volSliders, volNums,
	/*transitBus,*/ mixerGroup, currentSlValues;
	
	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		
		var numIns;
		var numOuts = 2;
		var bounds;
		var name = "HrStereoMixer";
		if(argExtraArgs.isNil, { numIns = 4; }, { numIns = 2 * argExtraArgs[0].asInteger; });
		bounds = Rect(200, 200, max(250, 50 + (20 * numIns)), 150);
		^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init;
	}
	
	init
	{
		
		window.background_(Color.gray(0.5));
		helpString = "Number of stereo outputs are set by an argument. Out 1/2 is all inputs mixed together.";
		
		synthInstances = List.new;
		volSliders = List.new;
		volNums = List.new;
		currentSlValues = List.new;
		mixerGroup = Group.new(target: group);
		
		// transitBus = Bus.audio(Server.default, 2);
		
		(inBusses.size/2).do
		({|cnt|
		
			
			volSliders.add(HrSlider(window, Rect(25+(40*cnt), 20, 40, 100)).value_(1)
				.action_
				({|sld| 
					currentSlValues[cnt] = sld.value;
					volNums[cnt].valueAction_(sld.value); 
				}) 
			);
			
			volNums.add(NumberBox(window, Rect(25+(40*cnt), 120, 40, 20)).value_(1)
				.action_
				({|nmb| 
					synthInstances[cnt].set(\mul, nmb.value); 
					volSliders[cnt].value_(nmb.value);
				}) 
			);
			
			currentSlValues.add(1);
		});
		
		fork
		{
			// really we don't need a unique synthdef per instance but... clean up later
			SynthDef("hrMixerInput"++uniqueID, 
			{
				arg inBusL, inBusR, pr_outBus0, pr_outBus1, mul=1;
				var inL = InFeedback.ar(inBusL);
				var inR = InFeedback.ar(inBusR);
				
				var smoothed = mul.lag(0.1);
				
				Out.ar(pr_outBus0, inL * smoothed);
				Out.ar(pr_outBus1, inR * smoothed);
				
			}).add;

			// badvalues synth will take care of this
			// SynthDef("hrMixerSummer"++uniqueID,
			// {
			// 	arg outBus0, outBus1;
			// 	var sound = [In.ar(transitBus.index), In.ar(transitBus.index+1)];
			// 	Out.ar(outBus0, sound[0]);
			// 	Out.ar(outBus1, sound[1]);
			// }).add;
			
			Server.default.sync;
			this.makeSynth;
			
		};
		
		saveGets = 
			volSliders.collect({|item| { item.value; }; }) ++
			volSliders.collect({|item| { item.boundMidiArgs; }; }) ++
			volSliders.collect({|item| { item.automationData; }; });
			
		saveSets = 
			volSliders.collect({|item| {|argg| item.valueAction_(argg); }; }) ++
			volSliders.collect({|item| {|argg| item.boundMidiArgs_(argg); }; }) ++
			volSliders.collect({|item| {|argg| item.automationData_(argg); }; }); 
			
		volSliders.size.do
		({|cnt|
		
			modGets.put(("level"++cnt).asSymbol, { currentSlValues[cnt]; });
			modSets.put(("level"++cnt).asSymbol, 
			{|argg| 
				
				synthInstances[cnt].set(\mul, argg); 
				currentSlValues[cnt] = argg;
				
				{ volSliders[cnt].value_(argg) }.defer;
			});
			modMapSets.put(("level"++cnt).asSymbol, 
			{|argg| 
				currentSlValues[cnt] = argg;
				{ volSliders[cnt].value_(argg) }.defer;
			});
		});
	}

	releaseSynth {
		if(synthInstances.size > 0) {
			synthInstances.do(_.free);
			synthInstances.clear;  // now size == 0
		};
	}

	makeSynth {
		(inBusses.size/2).do
		({|cnt|
			
			synthInstances.add
			(
				Synth("hrMixerInput"++uniqueID, 
					[
						\inBusL, inBusses[(0 + (cnt*2))], 
						\inBusR, inBusses[(1 + (cnt*2))],
						\pr_outBus0, outBusses[0],
						\pr_outBus1, outBusses[1],
						\mul, 1
					], target: mixerGroup)
			);			
			
		});
		
		// summerSynth = 
		// Synth("hrMixerSummer"++uniqueID, 
		// 	[
		// 		\outBus0, outBusses[0], 
		// 		\outBus1, outBusses[1]
		// 	],
		// 	mixerGroup,
		// 	\addToTail);
	}

	updateBusConnections
	{
		// summerSynth.set(\outBus0, outBusses[0], \outBus1, outBusses[1]);
	}
	
	cleanUp
	{
		mixerGroup.free;
	}
}