
w = Window(\test, Rect(800, 100, 400, 300));
v = HrListSelector(w, w.view.bounds.insetBy(2, 2));
w.front;

v.action = { |view|
	"".postln;
	[view.allItems, view.availItems, view.activeItems].do { |row|
		row.postln
	};
};

v.availItems = ["A", "B", "C"];