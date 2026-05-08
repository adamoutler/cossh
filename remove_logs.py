import os
import glob
import re

def balance_parens(s, start_index):
    count = 0
    in_string = False
    escape = False
    
    for i in range(start_index, len(s)):
        c = s[i]
        if escape:
            escape = False
            continue
            
        if c == '\\':
            escape = True
        elif c == '"':
            in_string = not in_string
        elif not in_string:
            if c == '(':
                count += 1
            elif c == ')':
                count -= 1
                if count == 0:
                    return i
    return -1

def split_args(s):
    args = []
    current = []
    count = 0
    in_string = False
    escape = False
    
    for c in s:
        if escape:
            current.append(c)
            escape = False
            continue
            
        if c == '\\':
            escape = True
            current.append(c)
        elif c == '"':
            in_string = not in_string
            current.append(c)
        elif not in_string:
            if c == '(':
                count += 1
                current.append(c)
            elif c == ')':
                count -= 1
                current.append(c)
            elif c == ',' and count == 0:
                args.append(''.join(current).strip())
                current = []
            else:
                current.append(c)
        else:
            current.append(c)
    if current:
        args.append(''.join(current).strip())
    return args

def replace_logs(filepath):
    with open(filepath, 'r') as f:
        content = f.read()
        
    modified = False
    
    while True:
        match = re.search(r'(?:android\.util\.)?Log\.[edviw]\s*\(', content)
        if not match:
            # Check for import android.util.Log and remove it
            import_stmt = "import android.util.Log\n"
            if import_stmt in content:
                content = content.replace(import_stmt, "")
                modified = True
            break
            
        start_idx = match.end() - 1 # The '('
        end_idx = balance_parens(content, start_idx)
        
        if end_idx == -1:
            print(f"Warning: unbalanced parens in {filepath}")
            break
            
        arg_string = content[start_idx+1:end_idx]
        args = split_args(arg_string)
        
        # Build the replacement println
        # Example: Log.e("Tag", "msg", e) -> println("Tag" + "msg" + e)
        # But we want to format it nicely and handle exceptions so they are printed or at least referenced.
        if len(args) == 1:
            repl = f"println({args[0]})"
        elif len(args) == 2:
            # To handle types, just string templates or toString()
            # Since args could be complex expressions, adding .toString() might not work if it's already a string.
            # Using string interpolation is safer: println("${args[0]}: ${args[1]}")
            # But args might have quotes. We can just use string concatenation.
            repl = f"println(({args[0]}).toString() + \": \" + ({args[1]}).toString())"
        elif len(args) == 3:
            repl = f"println(({args[0]}).toString() + \": \" + ({args[1]}).toString() + \" \" + ({args[2]}).toString())"
        else:
            repl = f"println(\"Log removed\")"
        
        content = content[:match.start()] + repl + content[end_idx+1:]
        modified = True

    if modified:
        with open(filepath, 'w') as f:
            f.write(content)
        print(f"Modified {filepath}")

for root, dirs, files in os.walk('app/src/main/kotlin'):
    for file in files:
        if file.endswith('.kt'):
            replace_logs(os.path.join(root, file))

