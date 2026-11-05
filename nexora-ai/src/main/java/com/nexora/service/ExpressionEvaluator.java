package com.nexora.service;

import java.util.ArrayList;
import java.util.List;

/** Safe recursive-descent calculator: + - * / % ^ ( ), constants pi/e, and common functions. No code execution. */
public final class ExpressionEvaluator {
    private final String s;
    private int p = 0;

    private ExpressionEvaluator(String s) { this.s = s; }

    public static double eval(String expr) {
        String c = expr.replace("×", "*").replace("÷", "/").replace("**", "^").replaceAll("\\s+", "");
        if (c.isEmpty()) throw new IllegalArgumentException("Empty expression");
        ExpressionEvaluator ev = new ExpressionEvaluator(c);
        double v = ev.expr();
        if (ev.p != c.length()) throw new IllegalArgumentException("Unexpected '" + c.charAt(ev.p) + "' at position " + ev.p);
        if (Double.isNaN(v) || Double.isInfinite(v)) throw new ArithmeticException("Result is not a finite number");
        return v;
    }

    private boolean eat(char c) {
        if (p < s.length() && s.charAt(p) == c) { p++; return true; }
        return false;
    }

    private double expr() {
        double v = term();
        while (true) {
            if (eat('+')) v += term();
            else if (eat('-')) v -= term();
            else return v;
        }
    }

    private double term() {
        double v = unary();
        while (true) {
            if (eat('*')) v *= unary();
            else if (eat('/')) {
                double d = unary();
                if (d == 0) throw new ArithmeticException("Division by zero");
                v /= d;
            } else if (eat('%')) {
                double d = unary();
                if (d == 0) throw new ArithmeticException("Modulo by zero");
                v %= d;
            } else return v;
        }
    }

    private double unary() {
        if (eat('-')) return -unary();
        if (eat('+')) return unary();
        return power();
    }

    private double power() {
        double b = primary();
        if (eat('^')) return Math.pow(b, unary());
        return b;
    }

    private double primary() {
        if (eat('(')) {
            double v = expr();
            if (!eat(')')) throw new IllegalArgumentException("Missing closing parenthesis");
            return v;
        }
        if (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) {
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            return Double.parseDouble(s.substring(st, p));
        }
        if (p < s.length() && Character.isLetter(s.charAt(p))) {
            int st = p;
            while (p < s.length() && Character.isLetter(s.charAt(p))) p++;
            String name = s.substring(st, p).toLowerCase();
            if (eat('(')) {
                List<Double> args = new ArrayList<>();
                if (!eat(')')) {
                    do { args.add(expr()); } while (eat(','));
                    if (!eat(')')) throw new IllegalArgumentException("Missing closing parenthesis");
                }
                return apply(name, args);
            }
            return switch (name) {
                case "pi" -> Math.PI;
                case "e" -> Math.E;
                default -> throw new IllegalArgumentException("Unknown name: " + name);
            };
        }
        throw new IllegalArgumentException(p < s.length() ? "Unexpected '" + s.charAt(p) + "'" : "Unexpected end of expression");
    }

    private double apply(String f, List<Double> a) {
        switch (f) {
            case "pow": need(f, a, 2); return Math.pow(a.get(0), a.get(1));
            case "max": need(f, a, 2); return Math.max(a.get(0), a.get(1));
            case "min": need(f, a, 2); return Math.min(a.get(0), a.get(1));
            default: break;
        }
        need(f, a, 1);
        double x = a.get(0);
        return switch (f) {
            case "sqrt" -> { if (x < 0) throw new ArithmeticException("sqrt of negative number"); yield Math.sqrt(x); }
            case "abs" -> Math.abs(x);
            case "sin" -> Math.sin(x);
            case "cos" -> Math.cos(x);
            case "tan" -> Math.tan(x);
            case "asin" -> Math.asin(x);
            case "acos" -> Math.acos(x);
            case "atan" -> Math.atan(x);
            case "ln" -> Math.log(x);
            case "log" -> Math.log10(x);
            case "exp" -> Math.exp(x);
            case "floor" -> Math.floor(x);
            case "ceil" -> Math.ceil(x);
            case "round" -> (double) Math.round(x);
            default -> throw new IllegalArgumentException("Unknown function: " + f);
        };
    }

    private void need(String f, List<Double> a, int n) {
        if (a.size() != n) throw new IllegalArgumentException(f + "() needs " + n + " argument(s)");
    }
}
