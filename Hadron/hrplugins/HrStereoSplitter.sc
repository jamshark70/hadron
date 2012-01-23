HrStereoSplitter : HadronPlugin
{
	var sourceSlider, parNumIns, volSliders, volNums, mixerGroup, currentSlValues;

	// special override: this has outputs, but we don't need them to be checked
	shouldCheckBad { ^false }

	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|

		var numIns = 2;
		var numOuts = 2;
		var bounds;
		var name = "HrStereoSplitter";
		if(argExtraArgs.isNil, { numOuts = 4; }, { numOuts = 2 * argExtraArgs[0].asInteger; });
		bounds = Rect(200, 200, max(250, 50 + (20 * numOuts)), 150);
		^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init;
	}

	init
	{

		window.background_(Color.gray(0.9));
		helpString = "Number of stereo outputs are set by an argument. Input is distributed to outputs in LRLR alignment.";
		synthInstance = List.new;
		volSliders = List.new;
		volNums = List.new;
		currentSlValues = List.fill(outBusses.size div: 2, 0);

		(outBusses.size/2).do
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
					synthInstance[cnt].set(\mul, nmb.value);
					volSliders[cnt].value_(nmb.value);
				})
			);
		});

		fork
		{
			SynthDef("hrSplitOut"++uniqueID,
			{
				arg inBus0, inBus1, outBusL, outBusR, mul=1;
				var inL = InFeedback.ar(inBus0);
				var inR = InFeedback.ar(inBus1);

				var smoothed = mul.lag(0.1);

				Out.ar(outBusL, inL * smoothed);
				Out.ar(outBusR, inR * smoothed);

			}).add;

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

				synthInstance[cnt].set(\mul, argg);
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
		if(synthInstance.size > 0) {
			synthInstance.do(_.free);
			synthInstance.clear;  // now size == 0
		};
	}

	makeSynth {
		this.releaseSynth;
		(outBusses.size/2).do
		({|cnt|

			synthInstance.add
			(
				Synth("hrSplitOut"++uniqueID,
					[
						\inBus0, inBusses[0],
						\inBus1, inBusses[1],
						\outBusL, mainOutBusses[0 + (cnt*2)],
						\outBusR, mainOutBusses[1 + (cnt*2)],
						\mul, 1
					], target: group)
			);

		});
	}

	updateBusConnections
	{
		(outBusses.size/2).do
		({|cnt|

			synthInstance[cnt].set
			(
				\outBusL, mainOutBusses[0 + (cnt*2)],
				\outBusR, mainOutBusses[1 + (cnt*2)]
			);

		});

	}

	cleanUp
	{
		//group will be freed for you
	}

	mapModCtl { |param, ctlBus|
		synthInstance[param.asString[5..].asInteger].tryPerform(
			\map, \mul, ctlBus
		)
	}
}