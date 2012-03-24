HrBuffer : HadronPlugin {
	var <buffer, <numFrames, bufferReady,
	infoLine, pathLine, loadButton, recordButton, recording = false, changeSizeView;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrBuffer", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 300).rand, (Window.screenBounds.height - 110).rand, 300, 110), 2, 2, argCanvasXY).init
	}
	init {
		var flow = FlowView(window, window.bounds),
		sec;

		bufferReady = Condition(false);

		infoLine = StaticText(flow, Rect(0, 0, 290, 20));

		if(extraArgs.size >= 1) {
			sec = extraArgs[0];
		} {
			sec = 2;
		};
		this.makeBuffer(sec);

		pathLine = StaticText(flow, Rect(0, 0, 290, 20))
		.string_("(no file)");
		
		loadButton = Button.new(flow, Rect(0, 0, 100, 20))
		.states_([["Load file"]])
		.action_({
			File.openDialog(nil, { |path|
				this.loadBuffer(path);
			})
		});

		recordButton = Button(flow, Rect(0, 0, 100, 20))
		.states_([
			["rec paused"],
			["recording", Color.black, Color(0.8, 1, 0.8)]
		])
		.action_({ |view|
			recording = view.value > 0;
			synthInstance.set(\record, view.value);
		});

		flow.startRow;

		StaticText(flow, Rect(0, 0, 100, 20)).string_("Resize buffer");
		changeSizeView = NumberBox(flow, Rect(0, 0, 100, 20))
		.maxDecimals_(3)
		.action_({ |view|
			this.makeBuffer(view.value);
		});

		this.makeSynth;

		saveGets = [
			{ numFrames }, { buffer.path }, { recording }
		];
		saveSets = [
			{ |argg| this.makeBuffer(argg / Server.default.sampleRate) },
			{ |argg| if(argg.notNil and: { argg != "" }) { this.loadBuffer(argg) } },
			{ |argg| recordButton.valueAction = argg.binaryValue }
		];

		modGets = (
			record: { recording.binaryValue }
		);
		modMapSets = (
			record: { |argg| synthInstance.set(\record, argg) }
		);
		modSets = (
			record: { |argg| synthInstance.set(\record, argg); defer { recordButton.value = argg.sign } }
		);
	}

	makeBuffer { |sec = 2|
		var newBuffer;
		if(buffer.isNil) {
			fork {
				bufferReady.test = false;
				numFrames = sec * Server.default.sampleRate;
				buffer = Buffer.alloc(Server.default, numFrames, 1);
				Server.default.sync;
				bufferReady.test_(true).signal;
				defer {
					infoLine.string_("Buffer %: % sec, % samples".format(buffer.bufnum, sec.round(0.001), numFrames));
					changeSizeView.value = buffer.duration;
				};
			};
		} {
			if(sec.round(0.001) != buffer.duration.round(0.001)) {
				{
					bufferReady.test = false;
					numFrames = sec * Server.default.sampleRate;
					newBuffer = Buffer.alloc(Server.default, numFrames, 1);
					Server.default.sync;
					buffer.copyData(newBuffer, 0, 0, -1);
					Server.default.sync;
					synthInstance.set(\bufnum, newBuffer.bufnum);
					infoLine.string = "Buffer %: % sec, % samples".format(
						buffer.bufnum,
						(numFrames / Server.default.sampleRate).round(0.001),
						numFrames
					);
					buffer.free;
					buffer = newBuffer;
					bufferReady.test_(true).signal;
					infoLine.string_("Buffer %: % sec, % samples".format(buffer.bufnum, sec.round(0.001), numFrames));
					changeSizeView.value = buffer.duration;
				}.fork(AppClock);
			};
		};

		defer {  };
	}

	loadBuffer { |path|
		var loadFailed = true, sf,
		postLoad = {
			loadFailed = false;
			defer {
				parentApp.displayStatus("'%' loaded successfully!".format(path.basename), 1);
				pathLine.string = path.basename;
			};
		};
		fork {
			bufferReady.wait;
			sf = SoundFile.openRead(path);
			if(sf.notNil) {
				sf.close;
				if(sf.numChannels == 1) {
					buffer.read(path, fileStartFrame: 0, numFrames: buffer.numFrames, action: postLoad);
				} {
					buffer.readChannel(path, channels: #[0], action: postLoad)
				};
			};
			AppClock.sched(2.0, {
				if(loadFailed) {
					parentApp.displayStatus("'%' load failed".format(path.basename), -1)
				};
			});
		};
	}

	synthArgs { ^[inBus0: inBusses[0], outBus0: outBusses[0], bufnum: buffer.bufnum, record: recording.binaryValue] }

	makeSynthDef {
		SynthDef(\HrBuffer, { |inBus0, outBus0, bufnum, record = 0|
			var input = InFeedback.ar(inBus0, 2),
			runRec = ((record > 0) + (input[1] > 0)) > 0,
			phase = Phasor.ar(rate: runRec, start: 0, end: BufFrames.kr(bufnum)),
			oldBufData = BufRd.ar(1, bufnum, phase, loop: 0, interpolation: 1);

			BufWr.ar(Select.ar(runRec, [oldBufData, input[0]]), bufnum, phase, loop: 0);
			Out.ar(outBus0, [K2A.ar(bufnum), phase]);
		}).add;
	}

	defName { ^"HrBuffer" }

	updateBusConnections { synthInstance.set(*this.synthArgs) }
	cleanUp { buffer.free }
}