import java.util.Arrays;
import java.util.stream.Collectors;

public class Test {
    public static class Person implements Cloneable{
        public String pname;
        public int page;
        public Address address;
        public Person() {}

        public Person(String pname,int page){
            this.pname = pname;
            this.page = page;
            this.address = new Address();
        }

        @Override
        protected Object clone() throws CloneNotSupportedException {
            return super.clone();
        }

        public void setAddress(String provices,String city ){
            address.setAddress(provices, city);
        }
        public void display(String name){
            System.out.println(name+":"+"pname=" + pname + ", page=" + page +","+ address);
        }

        public String getPname() {
            return pname;
        }

        public void setPname(String pname) {
            this.pname = pname;
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

    }

    public static class Address {
        private String provices;
        private String city;
        public void setAddress(String provices,String city){
            this.provices = provices;
            this.city = city;
        }
        @Override
        public String toString() {
            return "Address [provices=" + provices + ", city=" + city + "]";
        }

    }

    public static void main(String[] args) throws CloneNotSupportedException {
        String s = """
abstract
as
base
bool
break
byte
case
catch
char
checked
class
const
continue
decimal
default
delegate
do
double
else
enum
event
explicit
extern
false
finally
fixed
float
for
foreach
goto
if
implicit
in
int
interface
internal
is
lock
long
namespace
new
null
object
operator
out
override
params
private
protected
public
readonly
ref
return
sbyte
sealed
short
sizeof
stackalloc
static
string
struct
switch
this
throw
true
try
typeof
uint
ulong
unchecked
unsafe
ushort
using
virtual
void
volatile
while
add
and
alias
ascending
async
await
by
descending
dynamic
equals
from
get
global
group
init
into
join
let
nameof
nint
not
notnull
nuint
on
or
orderby
partial
record
remove
select
set
unmanaged
value
var
when
where
with
yield
                """;
        String[] ss = s.split("\n");
        StringBuilder output = new StringBuilder();
        Arrays.stream(ss).distinct().forEach(e -> {
            output.append("\"").append(e).append("\", ");
        });
        System.out.println(output);
    }
}
