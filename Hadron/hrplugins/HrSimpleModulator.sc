HrSimpleModulator : HadronPlugin
{
	var <synthInstance, prOutBus, postOpText, postOpFunc, pollRoutine,
	modControl, startButton;
	
	*new
	{|argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		
		var numIns = 1;
		var numOuts = this.numOuts;
		var height = this.height(argExtraArgs);
		var bounds = Rect((Window.screenBounds.width - 450).rand, (Window.screenBounds.height - height).rand, 450, height);
		var name = this.name;
		^super.new(argParentApp, name, argIdent, argUniqueID, argExtraArgs, bounds, numIns, numOuts, argCanvasXY).init;
	}
	*height { ^115 }
	*numOuts { ^0 }
	
	init
	{
		window.background_(Color.gray(0.7));
		prOutBus = Bus.control(Server.default, 1);
		helpString = "Input is modulation source (audio). Applies the operation and modulates the target parameter.";
		StaticText(window, Rect(10, 20, 150, 20)).string_("Operation on signal \"sig\":");
		
		postOpFunc = {|sig| (sig * 0.5) + 0.5; };
		
		postOpText = TextField(window, Rect(160, 20, 280, 20)).string_("(sig * 0.5) + 0.5;")
		.action_({|txt| postOpFunc = ("{|sig|"+ txt.value + "}").interpret; });
		
		modControl = HadronModTargetControl(window, Rect(10, 50, 430, 20), parentApp, this);
		
		startButton = Button(window, Rect(10, 80, 80, 20)).states_([["Start"],["Stop"]])
		.action_
		({|btn|
		
			if(btn.value == 1, { pollRoutine.play(AppClock); }, { fork{ pollRoutine.stop; 0.1.wait; pollRoutine.reset; } });
		});
		
		
		
		pollRoutine = 
		Routine
		({
			loop
			{
				prOutBus.get({|val| { modControl.modulateWithValue(postOpFunc.value(val)); }.defer; });
				0.04.wait;
			}
		});
		
		fork
		{
			SynthDef("hrSimpleMod"++uniqueID,
			{
				arg inBus0;
				var input = InFeedback.ar(inBus0);
				Out.kr(prOutBus, A2K.kr(input));
				
			}).add;
			
			Server.default.sync;
			this.makeSynth;
		};
		
		saveGets =
			[
				{ postOpText.string; },
				{ modControl.getSaveValues; },
				{ startButton.value; }
			];
		
		saveSets =
			[
				{|argg| postOpText.valueAction_(argg); },
				{|argg| modControl.putSaveValues(argg); },
				{|argg| startButton.valueAction_(argg); }
			];
	}

	releaseSynth { synthInstance.free; synthInstance = nil; }

	makeSynth {
		this.releaseSynth;
		synthInstance = Synth("hrSimpleMod"++uniqueID, [\inBus0, inBusses[0]], target: group);
	}
	
	notifyPlugKill
	{|argPlug|
		
		modControl.plugRemoved(argPlug);
	}
	
	notifyPlugAdd
	{|argPlug|
		modControl.plugAdded;
	}
	
	wakeFromLoad
	{
		modControl.doWakeFromLoad;
	}
	
	
	updateBusConnections
	{
		synthInstance.set(\inBus0, inBusses[0]);
	}
	
	cleanUp
	{
		synthInstance.free;
		pollRoutine.stop;
	}

	fixFuncString { |str|
		var i = str.detectIndex { |ch| ch.isSpace.not },
		j = str.size;
		while {
			j = j - 1;
			j > 0 and: { str[j].isSpace }
		};
		^"%%%".format(
			if(str[i] != ${) { "{ " } { "" },
			str[i..j],
			if(str[j] != $}) { " }" } { "" }
		)
	}
}