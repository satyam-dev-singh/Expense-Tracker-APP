import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

public class AnalyticsHarness {
  static int passed=0; static List<String> failures=new ArrayList<>();
  enum Type { EXPENSE, INCOME } enum Cycle { WEEKLY, MONTHLY, QUARTERLY, YEARLY }
  record Tx(Type type,long amount,String category,LocalDate date,boolean recurring) {}
  record Sub(String name,long amount,Cycle cycle,boolean active) {}
  record Slice(String category,long amount,double percent) {}
  static boolean inMonth(LocalDate d, YearMonth m){return !d.isBefore(m.atDay(1))&&d.isBefore(m.plusMonths(1).atDay(1));}
  static List<Slice> distribution(List<Tx> txs, Map<String,String> names, YearMonth m){Map<String,Long> sums=new HashMap<>(); long total=0; for(Tx t:txs) if(t.type==Type.EXPENSE&&inMonth(t.date,m)){String n=t.category==null?"Uncategorized":names.getOrDefault(t.category,"Uncategorized"); sums.put(n,Math.addExact(sums.getOrDefault(n,0L),t.amount)); total=Math.addExact(total,t.amount);} List<Slice> out=new ArrayList<>(); for(var e:sums.entrySet()) out.add(new Slice(e.getKey(),e.getValue(), total==0?0:Math.round(e.getValue()*10000.0/total)/100.0)); out.sort((a,b)->Long.compare(b.amount,a.amount)); return out;}
  static long expenses(List<Tx> txs, YearMonth m){long s=0; for(Tx t:txs) if(t.type==Type.EXPENSE&&inMonth(t.date,m)) s=Math.addExact(s,t.amount); return s;}
  static long income(List<Tx> txs, YearMonth m){long s=0; for(Tx t:txs) if(t.type==Type.INCOME&&inMonth(t.date,m)) s=Math.addExact(s,t.amount); return s;}
  static long projectedMonthly(Sub s){return switch(s.cycle){case WEEKLY->Math.multiplyExact(s.amount,4);case MONTHLY->s.amount;case QUARTERLY->s.amount/3;case YEARLY->s.amount/12;};}
  static long recurringTotal(List<Tx> txs,List<Sub> subs,YearMonth m){long s=0; for(Tx t:txs) if(t.type==Type.EXPENSE&&t.recurring&&inMonth(t.date,m)) s=Math.addExact(s,t.amount); for(Sub sub:subs) if(sub.active) s=Math.addExact(s,projectedMonthly(sub)); return s;}
  static String insight(List<Tx> txs, Map<String,String> names, YearMonth m){var dist=distribution(txs,names,m); long exp=expenses(txs,m), inc=income(txs,m); if(exp==0) return "No spending recorded yet for "+m.getMonth(); String top=dist.get(0).category; long left=inc-exp; return top+" is your biggest spend area; "+(left>=0?"you are still positive this month.":"expenses are above income this month.");}
  static void eq(String n,Object e,Object a){if(Objects.equals(e,a))passed++;else{failures.add(n+" expected "+e+" got "+a);}}
  static void close(String n,double e,double a){if(Math.abs(e-a)<0.01)passed++;else failures.add(n+" expected "+e+" got "+a);} 
  public static void main(String[] args){YearMonth mar=YearMonth.of(2024,3); Map<String,String> names=Map.of("food","Food","travel","Travel"); List<Tx> txs=List.of(new Tx(Type.INCOME,5000000,null,LocalDate.of(2024,3,1),false),new Tx(Type.EXPENSE,1000000,"food",LocalDate.of(2024,3,3),false),new Tx(Type.EXPENSE,500000,"travel",LocalDate.of(2024,3,4),false),new Tx(Type.EXPENSE,500000,"food",LocalDate.of(2024,3,5),true),new Tx(Type.EXPENSE,999999,"food",LocalDate.of(2024,4,1),false)); var d=distribution(txs,names,mar); eq("slice count",2,d.size()); eq("top category","Food",d.get(0).category); eq("food total",1500000L,d.get(0).amount); close("food percent",75.0,d.get(0).percent); eq("mom delta",1000001L,expenses(txs,mar)-expenses(txs,YearMonth.of(2024,4))); eq("remaining",3000000L,income(txs,mar)-expenses(txs,mar)); eq("monthly sub",90000L,projectedMonthly(new Sub("Music",90000,Cycle.MONTHLY,true))); eq("yearly sub floor",10000L,projectedMonthly(new Sub("Cloud",120000,Cycle.YEARLY,true))); eq("recurring total",590000L,recurringTotal(txs,List.of(new Sub("Music",90000,Cycle.MONTHLY,true)),mar)); eq("insight","Food is your biggest spend area; you are still positive this month.",insight(txs,names,mar)); System.out.println("PASSED: "+passed+" assertions"); if(!failures.isEmpty()){failures.forEach(System.out::println); System.exit(1);} System.out.println("ALL ANALYTICS CHECKS PASSED");}
}
