GUI.qt;

g.boot;
GUI.swing;


(
w = Window(\test, Rect(700, 100, 600, 400));
z = HrPatternLine(w, Rect(2, 2, 596, 24), "test", nil, "Pwhite.new", 0);
w.front;
)

z.isLast = true;
z.isLast = false;

z.index = 8;

z.model
z.model.source = Pexprand.new;

z.remove;

f = { |...args| args.debug("dependant") };

z.addDependant(f);
z.removeDependant(f);

x = z.model;

x.dependants;

