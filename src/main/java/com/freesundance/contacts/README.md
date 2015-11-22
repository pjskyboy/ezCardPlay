Stories :-)
===========

Google is master copy - Phone CRUD ops are overridden by changes in Google

Export to VCF format from:
    Google
    Phone
    
CRUD ops
    Create Google from VCF entry
    Read Google entry from VCF key fields
        1) Name
        2) Number(s)
        3) Others?
    Update Google from VCF entry (see merge below)
    Delete Google entry / entries
        
Merge
    Extract Google VCF
    Extract Phone VCF
    Diff files
        1) Generate CRUD "script" for Google
        2) Generate VCF upload file for Phone
    
Bulk Upload
    Google
        1) bootstrap nostro contacts
        2) Clean recovery after failure
    Phone
        1) Only mechanism for update 
