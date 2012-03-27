HrBuffer : HadronPlugin {
	var <buffer, <numFrames, bufferReady,
	recording = false, loopRecord = true,
	recordResp,
	infoLine, pathLine, loadButton, recordButton, loopButton, changeSizeView, mixSl;

	*initClass { this.addHadronPlugin }

	*new { |argParentApp, argIdent, argUniqueID, argExtraArgs, argCanvasXY|
		^super.new(argParentApp, "HrBuffer", argIdent, argUniqueID, argExtraArgs, Rect((Window.screenBounds.width - 300).rand, (Window.screenBounds.height - 140).rand, 300, 140), 2, 2, argCanvasXY).init
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
		
		loadButton = Button(flow, Rect(0, 0, 94, 20))
		.states_([["Load file"]])
		.action_({
			File.openDialog(nil, { |path|
				this.loadBuffer(path);
			})
		});

		Button(flow, Rect(0, 0, 94, 20))
		.states_([["Save file"]])
		.action_({
			var resp, saveFailed = true;
			File.saveDialog(nil, nil, { |path|
				resp = OSCpathResponder(Server.default.addr, ['/b_info', buffer.bufnum], {
					saveFailed = false;
					resp.remove;
					buffer.path = path;
					defer {
						parentApp.displayStatus("Saved to %".format(path.basename), 1);
						pathLine.string = path.basename;
					};
				}).add;
				buffer.write(path, headerFormat: "WAV", completionMessage: { |buf|
					[\b_query, buf.bufnum]
				});
				AppClock.sched(2.0, {
					if(saveFailed) {
						resp.remove;
						parentApp.displayStatus("Save to % failed".format(path.basename), -1);
					};
					nil
				});
			});
		});

		flow.startRow;

		recordButton = Button(flow, Rect(0, 0, 94, 20))
		.states_([
			["rec paused"],
			["recording", Color.black, Color(0.8, 1, 0.8)]
		])
		.action_({ |view|
			recording = view.value > 0;
			// reset phasor only if not looping
			synthInstance.set(
				\record, view.value,
				\t_recordGo, (loopRecord.not and: recording).binaryValue
			);
		});

		loopButton = Button(flow, Rect(0, 0, 94, 20))
		.states_([["no looping"], ["looping"]])
		.value_(loopRecord.binaryValue)
		.action_({ |view|
			loopRecord = view.value > 0;
			synthInstance.set(\loop, view.value);
		});

		flow.startRow;

		mixSl = HrEZSlider(flow,
			Rect(0, 0, flow.bounds.width - (2 * flow.decorator.margin.x), 20),
			"rec mix", #[0, 1],
			{ |view|
				synthInstance.set(\mix, view.value);
			}, initVal: 1
		);

		flow.startRow;

		StaticText(flow, Rect(0, 0, 100, 20)).string_("Resize buffer");
		changeSizeView = NumberBox(flow, Rect(0, 0, 100, 20))
		.maxDecimals_(3)
		.action_({ |view|
			this.makeBuffer(view.value);
		});

		recordResp = OSCresponderNode(Server.default.addr, '/recordState', { |time, resp, msg|
			if(msg[1] == synthInstance.tryPerform(\nodeID)) {
				defer { recordButton.valueAction = msg[3] };
			};
		}).add;

		this.makeSynth;

		saveGets = [
			{ numFrames },
			{ buffer.path },
			{ loopRecord },
			{ mixSl.value },
			{ recording }
		];
		saveSets = [
			{ |argg| this.makeBuffer(argg / Server.default.sampleRate) },
			{ |argg| if(argg.notNil and: { argg != "" }) { this.loadBuffer(argg) } },
			{ |argg| loopButton.valueAction = argg.binaryValue },
			{ |argg| mixSl.valueAction = argg },
			{ |argg| recordButton.valueAction = argg.binaryValue }
		];

		modGets = (
			record: { recording.binaryValue },
			loop: { loopRecord.binaryValue },
			mix: { mixSl.value }
		);
		modMapSets = (
			record: { |argg| defer { recordButton.value = argg.sign } },
			loop: { |argg| defer { loopButton.value = argg.sign } },
			mix: { |argg| defer { mixSl.value = argg } }
		);
		modSets = (
			record: { |argg| synthInstance.set(\record, argg); defer { recordButton.value = argg.sign } },
			loop: { |argg| synthInstance.set(\loop, argg); defer { loopButton.value = argg.sign } },
			mix: { |argg| synthInstance.set(\mix, argg); defer { mixSl.value = argg } }
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

	synthArgs { ^[inBus0: inBusses[0], outBus0: outBusses[0], bufnum: buffer.bufnum, record: recording.binaryValue, loop: loopRecord.binaryValue, mix: mixSl.value] }

	makeSynthDef {
		SynthDef(\HrBuffer, { |inBus0, outBus0, bufnum, record = 0, loop = 1, mix = 1,
			t_recordGo = 0|
			var input = InFeedback.ar(inBus0, 2),
			runRec = ((record > 0) + (input[1] > 0)) > 0,
			bufFr = BufFrames.kr(bufnum),
			endpos = bufFr + (1000 * (loop <= 0)),
			phase, oldBufData, newSig, oldphase;

			oldphase = LocalIn.ar(1);
			phase = Phasor.ar(t_recordGo, rate: runRec * (oldphase < bufFr), start: 0, end: endpos);
			oldBufData = BufRd.ar(1, bufnum, phase, loop: 0, interpolation: 1);

			runRec = runRec * (phase < bufFr);
			SendReply.ar(HPZ1.ar(runRec).abs, '/recordState', runRec);

			newSig = LinXFade2.ar(oldBufData, input[0], pan: mix.madd(2, -1));

			BufWr.ar(Select.ar(runRec, [oldBufData, newSig]), bufnum, phase, loop: 0);
			Out.ar(outBus0, [K2A.ar(bufnum), phase]);
		}).add;
	}

	defName { ^"HrBuffer" }

	updateBusConnections { synthInstance.set(*this.synthArgs) }
	cleanUp { buffer.free; recordResp.remove; }
}
