package com.droidkay.lumen;

import java.util.List;
import java.util.Map;

/**
 * One snapshot from the inverter, already scaled to real units. NaN = not reported.
 * Handles the different layouts Felicity firmwares use (checked against a real IVGM-class unit,
 * firmware 2.15, Type 80 / SubType 1035).
 */
final class Reading {
    long time;
    String raw = "";
    String serial;

    double soc = Double.NaN;          // %
    double battV = Double.NaN;        // V
    double battA = Double.NaN;        // A (magnitude)
    double battW = Double.NaN;        // W (magnitude, as reported)
    int chargeState = -1;             // bCStat: 1 = charging (others: idle / discharging)
    double battTemp = Double.NaN;     // °C
    double cellMax = Double.NaN, cellMin = Double.NaN; // V, from the battery's BMS
    double bmsAh = Double.NaN;        // battery capacity reported by the BMS
    double loadPct = Double.NaN;      // %
    double acInV = Double.NaN, acInA = Double.NaN, acInHz = Double.NaN, acInW = Double.NaN;
    double acOutV = Double.NaN, acOutA = Double.NaN, acOutHz = Double.NaN, acOutW = Double.NaN, acOutVA = Double.NaN;
    double pvV = Double.NaN, pvA = Double.NaN, pvW = Double.NaN;
    double busV = Double.NaN;
    double[] temps = {Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    String workMode, warn, fault, firmware;

    // energy today, kWh (from the inverter if it sends it; otherwise Lumen counts it itself)
    double solarToday = Double.NaN, gridToday = Double.NaN, battInToday = Double.NaN,
            battOutToday = Double.NaN, homeToday = Double.NaN;

    /** Grid (NEPA) present? */
    boolean gridOn() {
        return !Double.isNaN(acInV) && acInV > 150;
    }

    double load() {
        return Double.isNaN(acOutW) ? 0 : Math.max(0, acOutW);
    }

    double solar() {
        return Double.isNaN(pvW) ? 0 : Math.max(0, pvW);
    }

    private double battMagnitude() {
        if (!Double.isNaN(battW) && battW > 0) return battW;
        if (!Double.isNaN(battA) && !Double.isNaN(battV)) return battA * battV;
        return Double.NaN;
    }

    /** Battery power: + charging, - discharging. */
    double battery() {
        double mag = battMagnitude();
        double net = solar() + gridDirect() - load();
        if (Double.isNaN(mag)) {
            // no battery reading: estimate from the balance of what flows in and out
            if (Math.abs(net) < 15) return 0;
            return net < 0 ? net / 0.94 : net * 0.94;
        }
        if (mag < 15) return 0;
        if (chargeState == 1) return mag;
        if (!gridOn() && solar() < load()) return -mag;
        return net >= 0 ? mag : -mag;
    }

    /** Grid watts straight from the inverter, or volts x amps. 0 when the grid is off. */
    private double gridDirect() {
        if (!gridOn()) return 0;
        if (!Double.isNaN(acInW) && acInW > 5) return acInW;
        if (!Double.isNaN(acInA) && acInA > 0.05) return acInV * acInA * 0.95;
        return Double.NaN;
    }

    /** Grid power, falling back to the balance (home + battery charging - solar) if it isn't reported. */
    double grid() {
        if (!gridOn()) return 0;
        double g = gridDirect();
        if (!Double.isNaN(g)) return g;
        double mag = battMagnitude();
        double charge = Double.isNaN(mag) ? 0 : (chargeState == 1 ? mag : -mag);
        return Math.max(0, load() + charge - solar());
    }

    boolean hasFault() {
        return fault != null && !fault.equals("0") && !fault.isEmpty();
    }

    boolean hasWarning() {
        return warn != null && !warn.equals("0") && !warn.isEmpty();
    }

    private static double firstPositive(double... v) {
        for (double x : v) if (!Double.isNaN(x) && x > 0) return x;
        return Double.NaN;
    }

    static Reading parse(String text) {
        List<Map<String, Object>> objs = Json.objects(text);
        if (objs.isEmpty()) return null;
        Map<String, Object> d = objs.get(0);
        if (Json.at(d, "Batsoc") == null && Json.at(d, "ACout") == null && Json.at(d, "PV") == null) return null;

        Reading r = new Reading();
        r.time = System.currentTimeMillis();
        r.raw = text;
        r.serial = Json.str(d, "DevSN");
        if (r.serial == null) r.serial = Json.str(d, "wifiSN");

        r.soc = Json.num(d, 0.01, "Batsoc", 0, 0);
        r.battV = Json.num(d, 0.001, "Batt", 0, 0);
        r.loadPct = Json.num(d, 0.1, "lPerc");
        r.busV = Json.num(d, 0.1, "busVp");

        // Battery: Batt = [[mV], [A x10], [W, ...]] on newer firmware
        r.battA = Json.num(d, 0.1, "Batt", 1, 0);
        if (Double.isNaN(r.battA)) r.battA = Math.abs(Json.num(d, 0.1, "Batt", 0, 1));
        r.battW = Json.num(d, 1, "Batt", 2, 0);
        double cs = Json.num(d, 1, "bCStat");
        r.chargeState = Double.isNaN(cs) ? -1 : (int) cs;
        r.battTemp = Json.num(d, 1, "BatTem");

        // Grid in: [[V x10], [A x10], [Hz x100], [W, VA], [W]]
        r.acInV = Json.num(d, 0.1, "ACin", 0, 0);
        r.acInA = Json.num(d, 0.1, "ACin", 1, 0);
        r.acInHz = Json.num(d, 0.01, "ACin", 2, 0);
        r.acInW = firstPositive(Json.num(d, 1, "ACin", 3, 0), Json.num(d, 1, "ACin", 4, 0));
        if (Double.isNaN(r.acInW)) r.acInW = Json.num(d, 1, "ACin", 3, 0);

        // Output to the home: same layout
        r.acOutV = Json.num(d, 0.1, "ACout", 0, 0);
        r.acOutA = Json.num(d, 0.1, "ACout", 1, 0);
        r.acOutHz = Json.num(d, 0.01, "ACout", 2, 0);
        r.acOutW = firstPositive(Json.num(d, 1, "ACout", 3, 0), Json.num(d, 1, "ACout", 4, 0));
        if (Double.isNaN(r.acOutW)) r.acOutW = Json.num(d, 1, "ACout", 3, 0);
        r.acOutVA = Json.num(d, 1, "ACout", 3, 1);

        // Solar. Two layouts exist:
        //  A) per string: PV[n] = [V x10, A x10, W], total in PV[3][0]
        //  B) by quantity (this firmware): PV[0][0] = V x10, PV[1][0] = A x10, PV[2][0] = W
        double v0 = Json.num(d, 0.1, "PV", 0, 0);
        double i01 = Json.num(d, 0.1, "PV", 0, 1);
        double p02 = Json.num(d, 1, "PV", 0, 2);
        double i10 = Json.num(d, 0.1, "PV", 1, 0);
        double p20 = Json.num(d, 1, "PV", 2, 0);
        double total = Json.num(d, 1, "PV", 3, 0);
        boolean byQuantity = !Double.isNaN(v0) && (Double.isNaN(i01) || i01 == 0) && (Double.isNaN(p02) || p02 == 0)
                && !Double.isNaN(i10) && i10 < 60 && (!Double.isNaN(p20) && (p20 > v0 * 1.5 || i10 > 0 || p20 == 0));
        r.pvV = v0;
        if (byQuantity) {
            r.pvA = i10;
            r.pvW = firstPositive(p20, total, v0 * i10);
            if (Double.isNaN(r.pvW)) r.pvW = 0;
        } else {
            r.pvA = i01;
            double sum = 0;
            boolean any = false;
            for (int s = 0; s < 3; s++) {
                double p = Json.num(d, 1, "PV", s, 2);
                if (!Double.isNaN(p)) {
                    sum += Math.max(0, p);
                    any = true;
                }
            }
            r.pvW = firstPositive(total, any ? sum : Double.NaN, !Double.isNaN(i01) ? v0 * i01 : Double.NaN);
            if (Double.isNaN(r.pvW)) r.pvW = 0;
        }

        for (int k = 0; k < 4; k++) r.temps[k] = Json.num(d, 0.1, "Temp", 0, k);

        r.workMode = Json.str(d, "workM");
        r.warn = Json.str(d, "warn");
        r.fault = Json.str(d, "fault");

        r.solarToday = Json.num(d, 0.001, "Energy", 0, 2);
        r.gridToday = Json.num(d, 0.001, "Energy", 2, 2);
        r.battInToday = Json.num(d, 0.001, "Energy", 4, 2);
        r.battOutToday = Json.num(d, 0.001, "Energy", 5, 2);
        r.homeToday = Json.num(d, 0.001, "Energy", 7, 2);
        if (Double.isNaN(r.homeToday)) r.homeToday = Json.num(d, 0.001, "Energy", 1, 2);

        // Second object (Type 112) is the battery's BMS: cell voltages, temperature, capacity
        for (int k = 1; k < objs.size(); k++) {
            Map<String, Object> b = objs.get(k);
            if (Json.at(b, "BMaxMin") == null && Json.at(b, "BattList") == null) continue;
            r.cellMax = Json.num(b, 0.001, "BMaxMin", 0, 0);
            r.cellMin = Json.num(b, 0.001, "BMaxMin", 0, 1);
            double ah = Json.num(b, 0.001, "BatsocList", 0, 2);          // 100000 -> 100 Ah
            if (Double.isNaN(ah) || ah < 5 || ah > 2000) ah = Json.num(b, 0.1, "BatsocList", 0, 1);
            if (!Double.isNaN(ah) && ah > 5 && ah < 2000) r.bmsAh = ah;
            if (Double.isNaN(r.battTemp)) r.battTemp = Json.num(b, 0.1, "Templist", 0, 0);
            if (r.firmware == null) r.firmware = Json.str(b, "version");
            break;
        }
        if (r.firmware == null) r.firmware = Json.str(d, "version");
        return r;
    }
}
